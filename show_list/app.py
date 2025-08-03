import json
import os
from flask import Flask, render_template, request, redirect, url_for
from apscheduler.schedulers.background import BackgroundScheduler

app = Flask(__name__)

@app.after_request
def add_header(response):
    response.headers['Cache-Control'] = 'no-cache, no-store, must-revalidate'
    response.headers['Pragma'] = 'no-cache'
    response.headers['Expires'] = '0'
    return response

DATA_FILE = 'data.json'

def load_data():
    try:
        with open(DATA_FILE, 'r') as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return []

def save_data(data):
    with open(DATA_FILE, 'w') as f:
        json.dump(data, f, indent=4)

def scan_and_update_episodes():
    print("Scanning for new episodes...")
    shows = load_data()
    updated_shows = False
    for show in shows:
        if 'directory_path' in show and show['directory_path']:
            dir_path = show['directory_path']
            if os.path.isdir(dir_path):
                existing_episode_titles = {e['title'] for e in show['episodes']}
                for root, _, files in os.walk(dir_path):
                    for filename in files:
                        name, ext = os.path.splitext(filename)
                        if ext.lower() in ['.mp4', '.mkv', '.avi', '.mov', '.webm']:
                            if name not in existing_episode_titles:
                                new_episode = {
                                    'id': len(show['episodes']) + 1,
                                    'title': name,
                                    'watched': False
                                }
                                show['episodes'].insert(0, new_episode)
                                existing_episode_titles.add(name)
                                updated_shows = True
            else:
                print(f"Directory not found for {show['title']}: {dir_path}")
    if updated_shows:
        save_data(shows)
        print("New episodes found and updated.")
    else:
        print("No new episodes found.")

scheduler = BackgroundScheduler()
scheduler.add_job(func=scan_and_update_episodes, trigger="interval", hours=2)
scheduler.start()

@app.route('/')
def index():
    shows = load_data()
    return render_template('index.html', shows=shows)

@app.route('/show/<int:show_id>')
def show(show_id):
    shows = load_data()
    show = next((s for s in shows if s['id'] == show_id), None)
    if show:
        return render_template('show.html', show=show)
    return 'Show not found', 404

@app.route('/add_show', methods=['GET', 'POST'])
def add_show():
    if request.method == 'POST':
        shows = load_data()
        new_show = {
            'id': len(shows) + 1,
            'title': request.form['title'],
            'year': request.form.get('year', ''),
            'cover_image': request.form.get('cover_image', ''),
            'directory_path': request.form.get('directory_path', ''),
            'episodes': []
        }
        shows.append(new_show)
        save_data(shows)
        return redirect(url_for('index'))
    return render_template('add_show.html')

@app.route('/edit_show/<int:show_id>', methods=['GET', 'POST'])
def edit_show(show_id):
    shows = load_data()
    show = next((s for s in shows if s['id'] == show_id), None)
    if not show:
        return 'Show not found', 404
    if request.method == 'POST':
        show['title'] = request.form['title']
        show['year'] = request.form.get('year', '')
        show['cover_image'] = request.form.get('cover_image', '')
        show['directory_path'] = request.form.get('directory_path', '')
        save_data(shows)
        return redirect(url_for('index'))
    return render_template('edit_show.html', show=show)

@app.route('/delete_show/<int:show_id>')
def delete_show(show_id):
    shows = load_data()
    shows = [s for s in shows if s['id'] != show_id]
    save_data(shows)
    return redirect(url_for('index'))

@app.route('/add_episode/<int:show_id>', methods=['POST'])
def add_episode(show_id):
    shows = load_data()
    show = next((s for s in shows if s['id'] == show_id), None)
    if show:
        new_episode = {
            'id': len(show['episodes']) + 1,
            'title': request.form['title'],
            'watched': False
        }
        show['episodes'].insert(0, new_episode)
        save_data(shows)
        return redirect(url_for('show', show_id=show_id))
    return 'Show not found', 404

@app.route('/edit_episode/<int:show_id>/<int:episode_id>', methods=['GET', 'POST'])
def edit_episode(show_id, episode_id):
    shows = load_data()
    show = next((s for s in shows if s['id'] == show_id), None)
    if not show:
        return 'Show not found', 404
    episode = next((e for e in show['episodes'] if e['id'] == episode_id), None)
    if not episode:
        return 'Episode not found', 404
    if request.method == 'POST':
        episode['title'] = request.form['title']
        save_data(shows)
        return redirect(url_for('show', show_id=show_id))
    return render_template('edit_episode.html', show_id=show_id, episode=episode)

@app.route('/delete_episode/<int:show_id>/<int:episode_id>')
def delete_episode(show_id, episode_id):
    shows = load_data()
    show = next((s for s in shows if s['id'] == show_id), None)
    if show:
        show['episodes'] = [e for e in show['episodes'] if e['id'] != episode_id]
        save_data(shows)
        return redirect(url_for('show', show_id=show_id))
    return 'Show not found', 404

@app.route('/toggle_watched/<int:show_id>/<int:episode_id>')
def toggle_watched(show_id, episode_id):
    shows = load_data()
    show = next((s for s in shows if s['id'] == show_id), None)
    if show:
        episode = next((e for e in show['episodes'] if e['id'] == episode_id), None)
        if episode:
            episode['watched'] = not episode['watched']
            save_data(shows)
            return redirect(url_for('show', show_id=show_id))
    return 'Episode not found', 404

@app.route('/scan_manual/<int:show_id>')
def scan_manual(show_id):
    scan_and_update_episodes()
    return redirect(url_for('show', show_id=show_id))

if __name__ == '__main__':
    app.run(debug=True, port=5011)