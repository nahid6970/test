from flask import Flask, render_template, request, redirect
import sqlite3
import os
from flask import jsonify
import math

app = Flask(__name__, static_folder='C:\\Users\\nahid\\ms\\ms1\\scripts\\flask\\5005_GameARR\\static')
DB_PATH = r"C:\Users\nahid\ms\msBackups\gameARR\game.db"
GAMES_PER_PAGE = 20

# Ensure database exists and creates the necessary table
def recreate_table():
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.execute('''CREATE TABLE IF NOT EXISTS games_new (
                                     id INTEGER PRIMARY KEY AUTOINCREMENT,
                                     name TEXT UNIQUE,
                                     year INTEGER,
                                     image TEXT,
                                     rating REAL,
                                     progression TEXT,
                                     url TEXT,
                                     collection TEXT

                                 )''')
    c.execute('''INSERT INTO games_new (id, name, year, image, rating, progression, url, collection)
                                 SELECT id, name, year, image, rating,
                                        CASE progression
                                            WHEN 0 THEN 'Unplayed'
                                            WHEN 50 THEN 'Unfinished'
                                            WHEN 100 THEN 'Complete'
                                            ELSE CAST(progression AS TEXT)
                                        END, url FROM games''') # Convert existing progression
    c.execute('DROP TABLE games')
    c.execute('ALTER TABLE games_new RENAME TO games')
    conn.commit()
    conn.close()

def init_db():
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.execute('''CREATE TABLE IF NOT EXISTS games (
                                     id INTEGER PRIMARY KEY AUTOINCREMENT,
                                     name TEXT UNIQUE,
                                     year TEXT,
                                     image TEXT,
                                     rating INTEGER,
                                     progression TEXT DEFAULT 'Unplayed🆕',
                                     url TEXT,
                                     collection TEXT DEFAULT ''
                                     )''')
    conn.commit()
    conn.close()

init_db()
try:
    recreate_table()
except sqlite3.OperationalError:
    # Handle case where the table might not have existed initially
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    try:
        c.execute("ALTER TABLE games ADD COLUMN progression TEXT DEFAULT 'Unplayed🆕'")
        conn.commit()
    except sqlite3.OperationalError:
        pass # Column might already exist
    finally:
        conn.close()



@app.route('/')
def index():
    sort_by = request.args.get('sort_by', 'name')
    order = request.args.get('order', 'asc')
    query = request.args.get('query') # Get the search query
    collection_filter = request.args.get('collection')
    page = request.args.get('page', 1, type=int) # Get current page, default to 1
    # Toggle order for next click
    next_order = 'desc' if order == 'asc' else 'asc'
    if sort_by not in ['name', 'year', 'rating', 'added']:
        sort_by = 'name'
    order_clause = 'ASC' if order == 'asc' else 'DESC'
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row # To access columns by name
    c = conn.cursor()

    # Count total number of games
    sql_count = "SELECT COUNT(*) FROM games"
    conditions_count = []
    params_count = []
    if query:
        conditions_count.append("REPLACE(LOWER(name), ' ', '') LIKE ?")
        params_count.append('%' + query.lower().replace(' ', '') + '%')
    if collection_filter:
        conditions_count.append("collection = ?")
        params_count.append(collection_filter)
    if conditions_count:
        sql_count += " WHERE " + " AND ".join(conditions_count)
    c.execute(sql_count, tuple(params_count))
    total_games_count = c.fetchone()[0]
    total_pages = math.ceil(total_games_count / GAMES_PER_PAGE)

    offset = (page - 1) * GAMES_PER_PAGE

    sql_query = f"SELECT id, name, year, image, CAST(rating AS INTEGER) AS rating, progression, url, collection FROM games"
    conditions = []
    params = []
    if query:
        conditions.append("REPLACE(LOWER(name), ' ', '') LIKE ?")
        params.append('%' + query.lower().replace(' ', '') + '%')
    if collection_filter:
        conditions.append("collection = ?")
        params.append(collection_filter)

    if conditions:
        sql_query += " WHERE " + " AND ".join(conditions)

    if sort_by == 'added': # Sort by id for 'added' order
        sql_query += f" ORDER BY id {order_clause} LIMIT ? OFFSET ?"
    else:
        sql_query += f" ORDER BY {sort_by} COLLATE NOCASE {order_clause} LIMIT ? OFFSET ?"

    params.extend([GAMES_PER_PAGE, offset])
    c.execute(sql_query, tuple(params))
    games = c.fetchall()

    c.execute("SELECT DISTINCT collection FROM games WHERE collection != '' ORDER BY collection COLLATE NOCASE")
    collections = [row[0] for row in c.fetchall()]
    conn.close()
    return render_template('index.html', games=games, sort_by=sort_by, order=order, next_order=next_order, query=query, total_games=total_games_count, collections=collections, current_collection_filter=collection_filter, page=page, total_pages=total_pages)

@app.route('/add', methods=['POST'])
def add_game():
    name = request.form['name']
    year = request.form['year']
    image = request.form['image']
    url = request.form['url'] or 'http://192.168.0.101:5005'  # Default URL if empty
    rating_str = request.form.get('rating')
    progression = request.form.get('progression')
    collection = request.form.get('collection', '').strip()
    sort_by = request.args.get('sort_by', 'name')  # Get current sort_by
    order = request.args.get('order', 'asc')      # Get current order
    collection_filter = request.args.get('collection') # Get current collection filter
    query = request.args.get('query') # Get the search query

    rating = None
    if rating_str and rating_str.isdigit():
        try:
            rating = int(rating_str)
        except ValueError:
            pass

    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.execute("SELECT id FROM games WHERE name = ?", (name,))
    existing_game = c.fetchone()

    if existing_game:
        conn.close()
        c = sqlite3.connect(DB_PATH).cursor()
        c.execute(f"SELECT id, name, year, image, CAST(rating AS INTEGER) AS rating, progression, url, collection FROM games ORDER BY name COLLATE NOCASE ASC")
        games = c.fetchall()
        c.connection.close()
        return render_template('index.html', games=games, sort_by='name', order='asc', next_order='desc', error="A game with this name already exists.", current_collection_filter=collection_filter, query=query)
    else:
        c.execute("INSERT INTO games (name, year, image, rating, progression, url, collection) VALUES (?, ?, ?, ?, ?, ?, ?)",
                  (name, year, image, rating, progression, url, collection))
        conn.commit()
        conn.close()
        redirect_url = f'/?sort_by={sort_by}&order={order}'
        if collection_filter:
            redirect_url += f'&collection={collection_filter}'
        if query:
            redirect_url += f'&query={query}'
        return redirect(redirect_url)

@app.route('/edit/<int:game_id>', methods=['GET', 'POST'])
def edit_game(game_id):
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    collection_filter = request.args.get('collection')
    sort_by = request.args.get('sort_by', 'name')  # Get current sort_by
    order = request.args.get('order', 'asc')      # Get current order
    query = request.args.get('query')  # Get the query parameter

    if request.method == 'POST':
        name = request.form['name']
        year = request.form['year']
        image = request.form['image']
        url = request.form['url']
        rating_str = request.form.get('rating')
        rating = None
        if rating_str and rating_str.isdigit():
            try:
                rating = int(rating_str)
            except ValueError:
                pass

        progression = request.form.get('progression') # Get progression as string
        collection = request.form.get('collection', '').strip()

        # Check if a game with the new name already exists (excluding the current game being edited)
        c.execute("SELECT id FROM games WHERE name = ? AND id != ?", (name, game_id))
        existing_game = c.fetchone()

        if existing_game:
            conn.close()
            c.execute("SELECT name, year, image, rating, progression, url, collection FROM games WHERE id = ?", (game_id,))
            game_data = c.fetchone()
            return render_template('edit_game.html', game=game_data, game_id=game_id, error="A game with this name already exists.", current_collection_filter=collection_filter, sort_by=sort_by, order=order, query=query)
        else:
            c.execute("UPDATE games SET name = ?, year = ?, image = ?, rating = ?, progression = ?, url = ?, collection = ? WHERE id = ?",
                      (name, year, image, rating, progression, url, collection, game_id))
            conn.commit()
            conn.close()
            redirect_url = f'/?sort_by={sort_by}&order={order}'
            if collection_filter:
                redirect_url += f'&collection={collection_filter}'
            if query:
                redirect_url += f'&query={query}'
            return redirect(redirect_url)
    else:
        c.execute("SELECT name, year, image, rating, progression, url, collection FROM games WHERE id = ?", (game_id,))
        game = c.fetchone()
        conn.close()
        return render_template('edit_game.html', game=game, game_id=game_id, current_collection_filter=collection_filter, sort_by=sort_by, order=order, query=query)

@app.route('/delete/<int:game_id>')
def delete_game(game_id):
    conn = sqlite3.connect(DB_PATH)
    sort_by = request.args.get('sort_by', 'name')  # Get current sort_by
    order = request.args.get('order', 'asc')      # Get current order
    c = conn.cursor()
    collection_filter = request.args.get('collection') # Get the collection filter
    query = request.args.get('query') # Get the search query
    c.execute("DELETE FROM games WHERE id = ?", (game_id,))
    conn.commit()
    conn.close()
    redirect_url = f'/?sort_by={sort_by}&order={order}'
    if collection_filter:
        redirect_url += f'&collection={collection_filter}'
    if query:
        redirect_url += f'&query={query}'
    return redirect(redirect_url)

@app.route('/search')
def search_games():
    query = request.args.get('query')
    if query:
        return redirect(f'/?query={query}') # Redirect to the index page with the query
    else:
        return redirect('/')


@app.route('/get_collection_games')
def get_collection_games():
    game_id = request.args.get('game_id')
    collection_name = request.args.get('collection_name')
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.execute("SELECT id, name, year FROM games WHERE collection = ? ORDER BY year", (collection_name,))
    games = c.fetchall()
    conn.close()
    game_list = []
    for game in games:
        style = 'color: white; font-weight: bold;' if str(game[0]) == game_id else ''
        game_list.append({'id': game[0], 'name': game[1], 'year': game[2], 'style': style})
    return jsonify(game_list)

if __name__ == '__main__':
    app.run(host="0.0.0.0", port=5005, debug=True)