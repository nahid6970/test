import os
from flask import Flask, request, send_from_directory, redirect, url_for, flash, render_template_string
from werkzeug.utils import secure_filename

app = Flask(__name__)
app.secret_key = 'your_secret_key'  # Required for flashing messages

# Folder to store files permanently
DESKTOP_PATH = os.path.expanduser('~/Desktop')
SHARE_FOLDER = os.path.join(DESKTOP_PATH, 'ShareFolder')

if not os.path.exists(SHARE_FOLDER):
    os.makedirs(SHARE_FOLDER)

app.config['SHARE_FOLDER'] = SHARE_FOLDER

# HTML template within the Python file (instead of an external index.html)
html_template = '''
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <link rel="shortcut icon" href="https://cdn-icons-png.flaticon.com/512/2840/2840124.png" type="image/x-icon">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Awesome File Share</title>
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;700&display=swap');

        :root {
            --primary-color: #4CAF50;
            --primary-hover-color: #45a049;
            --danger-color: #FF5733;
            --danger-hover-color: #C70039;
            --background-color: #eef1f5;
            --card-background-color: #ffffff;
            --text-color: #333;
            --light-text-color: #777;
            --border-color: #ddd;
            --shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
        }

        body {
            font-family: 'Roboto', sans-serif;
            margin: 0;
            padding: 40px 20px;
            background-color: var(--background-color);
            color: var(--text-color);
            display: flex;
            flex-direction: column;
            align-items: center;
            min-height: 100vh;
            box-sizing: border-box;
        }

        .container {
            background-color: var(--card-background-color);
            border-radius: 12px;
            box-shadow: var(--shadow);
            padding: 30px 40px;
            width: 100%;
            max-width: 700px;
            box-sizing: border-box;
            margin-bottom: 30px;
        }

        h1 {
            color: var(--primary-color);
            text-align: center;
            margin-bottom: 30px;
            font-weight: 700;
        }

        form {
            margin-bottom: 25px;
            display: flex;
            flex-direction: column;
            gap: 15px;
        }

        input[type="file"] {
            padding: 12px;
            font-size: 1rem;
            border: 1px solid var(--border-color);
            border-radius: 8px;
            background-color: #f9f9f9;
            cursor: pointer;
            transition: border-color 0.3s ease;
        }

        input[type="file"]::-webkit-file-upload-button {
            background-color: var(--primary-color);
            color: white;
            padding: 8px 15px;
            border: none;
            border-radius: 6px;
            cursor: pointer;
            margin-right: 15px;
            transition: background-color 0.3s ease;
        }

        input[type="file"]::-webkit-file-upload-button:hover {
            background-color: var(--primary-hover-color);
        }

        .button-container {
            display: flex;
            gap: 15px;
            justify-content: center;
            margin-top: 15px;
        }

        button {
            padding: 12px 25px;
            font-size: 1rem;
            font-weight: 600;
            color: white;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            transition: background-color 0.3s ease, transform 0.2s ease;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
        }

        button:hover {
            transform: translateY(-2px);
        }

        button.upload-button {
            background-color: var(--primary-color);
        }

        button.upload-button:hover {
            background-color: var(--primary-hover-color);
        }

        button.clean-button {
            background-color: var(--danger-color);
        }

        button.clean-button:hover {
            background-color: var(--danger-hover-color);
        }

        .circular-progress-wrapper {
            display: flex;
            flex-direction: column;
            align-items: center;
            margin-top: 30px;
            margin-bottom: 20px;
        }

        .circular-progress {
            position: relative;
            width: 150px;
            height: 150px;
            border-radius: 50%;
            background: conic-gradient(var(--primary-color) 0%, #e0e0e0 0%);
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: inset 0 0 10px rgba(0,0,0,0.1);
        }

        .circular-progress::before {
            content: '';
            position: absolute;
            width: 120px; /* Inner circle size */
            height: 120px;
            border-radius: 50%;
            background-color: var(--card-background-color);
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }

        .progress-percentage {
            position: relative; /* To ensure it's above the pseudo-element */
            font-size: 2.2rem;
            font-weight: 700;
            color: var(--text-color);
            z-index: 1; /* Ensure text is on top */
        }

        #upload-status {
            margin-top: 20px;
            padding: 15px;
            background-color: #f0f8ff;
            border: 1px solid #cceeff;
            border-radius: 8px;
            text-align: center;
            color: var(--light-text-color);
            min-height: 50px; /* Ensure some height even when empty */
            display: flex;
            align-items: center;
            justify-content: center;
        }
        
        #upload-status p {
            margin: 0;
            font-weight: 500;
            color: var(--primary-color);
        }
        #upload-status p.error {
            color: var(--danger-color);
        }


        .file-list-section {
            background-color: var(--card-background-color);
            border-radius: 12px;
            box-shadow: var(--shadow);
            padding: 30px 40px;
            width: 100%;
            max-width: 700px;
            box-sizing: border-box;
        }

        .file-list-section h2 {
            text-align: center;
            color: var(--primary-color);
            margin-bottom: 25px;
            font-weight: 600;
            border-bottom: 1px solid var(--border-color);
            padding-bottom: 15px;
        }

        .file-list {
            list-style: none;
            padding: 0;
            margin: 0;
        }

        .file-item {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 12px 0;
            border-bottom: 1px solid #eee;
        }
        .file-item:last-child {
            border-bottom: none;
        }

        .download-link {
            color: #007BFF;
            text-decoration: none;
            font-weight: 500;
            transition: color 0.2s ease;
        }

        .download-link:hover {
            color: #0056b3;
            text-decoration: underline;
        }
        
        .flash-messages {
            list-style: none;
            padding: 0;
            margin: 0 0 20px 0;
        }
        .flash {
            font-weight: bold;
            margin-bottom: 10px;
            padding: 10px;
            border-radius: 8px;
            border: 1px solid;
            text-align: center;
        }
        .flash.success {
            color: var(--primary-color);
            background-color: #e6ffe6;
            border-color: var(--primary-color);
        }
        .flash.error {
            color: var(--danger-color);
            background-color: #ffe6e6;
            border-color: var(--danger-color);
        }
        .flash.warning {
            color: #FFA500; /* Orange */
            background-color: #fffacd; /* LemonChiffon */
            border-color: #FFD700; /* Gold */
        }
        .flash.info {
            color: #4682B4; /* SteelBlue */
            background-color: #e0f2f7; /* Light blue */
            border-color: #B0E0E6; /* PowderBlue */
        }
    </style>
</head>
<body>

    <div class="container">
        <h1>Awesome File Share</h1>

        {% with messages = get_flashed_messages(with_categories=true) %}
            {% if messages %}
                <ul class="flash-messages">
                    {% for category, message in messages %}
                        <li class="flash {{ category }}">{{ message }}</li>
                    {% endfor %}
                </ul>
            {% endif %}
        {% endwith %}

        <form id="upload-form">
            <input type="file" id="file-input" name="files" multiple required>

            <div class="button-container">
                <button type="submit" class="upload-button">Upload Files</button>
            </div>
        </form>

        <form id="clean-form" method="POST" action="/clean">
            <div class="button-container">
                <button type="submit" class="clean-button">Clean Directory</button>
            </div>
        </form>

        <div class="circular-progress-wrapper">
            <div class="circular-progress" id="circular-progress">
                <div class="progress-percentage" id="progress-percentage">0%</div>
            </div>
            <div id="upload-status">Upload status will appear here.</div>
        </div>
    </div>

    <div class="container file-list-section">
        <h2>Available Files</h2>
        <ul class="file-list">
            {% if files %}
                {% for file in files %}
                    <li class="file-item">
                        <a class="download-link" href="/uploads/{{ file }}">{{ file }}</a>
                    </li>
                {% endfor %}
            {% else %}
                <li class="file-item" style="justify-content: center; color: var(--light-text-color);">No files available.</li>
            {% endif %}
        </ul>
    </div>

    <script>
        document.getElementById("upload-form").addEventListener("submit", async function(event) {
            event.preventDefault();

            var files = document.getElementById("file-input").files;
            var totalFiles = files.length;
            if (totalFiles === 0) {
                alert("Please select at least one file to upload.");
                return;
            }

            var uploadedFilesCount = 0;
            var uploadStatusDiv = document.getElementById("upload-status");
            var progressCircle = document.getElementById("circular-progress");
            var progressText = document.getElementById("progress-percentage");

            uploadStatusDiv.innerHTML = `<p>Starting upload...</p>`;
            progressText.innerText = "0%";
            progressCircle.style.background = 'conic-gradient(var(--primary-color) 0%, #e0e0e0 0%)';

            for (var i = 0; i < totalFiles; i++) {
                var file = files[i];
                var formData = new FormData();
                formData.append("file", file); // Changed 'files' to 'file' as we are sending one at a time

                uploadStatusDiv.innerHTML = `<p>Preparing to upload: <strong>${file.name}</strong></p>`;

                try {
                    await new Promise((resolve, reject) => {
                        var xhr = new XMLHttpRequest();
                        xhr.open("POST", "/", true);

                        xhr.upload.onprogress = function(event) {
                            if (event.lengthComputable) {
                                var percentComplete = (event.loaded / event.total) * 100;
                                
                                // Calculate overall progress considering files already uploaded
                                var overallProgress = ((uploadedFilesCount * 100) + percentComplete) / totalFiles;
                                
                                progressCircle.style.background = `conic-gradient(var(--primary-color) ${overallProgress}%, #e0e0e0 ${overallProgress}%)`;
                                progressText.innerText = `${Math.round(overallProgress)}%`;

                                uploadStatusDiv.innerHTML = `<p>Uploading <strong>${file.name}</strong>: ${Math.round(percentComplete)}%</p>`;
                            }
                        };

                        xhr.onload = function() {
                            if (xhr.status === 200) {
                                uploadedFilesCount++;
                                uploadStatusDiv.innerHTML = `<p style="color: var(--primary-color);"><strong>${file.name}</strong> uploaded successfully!</p>`;
                                resolve();
                            } else {
                                uploadStatusDiv.innerHTML = `<p class="error">Error uploading <strong>${file.name}</strong>. Status: ${xhr.status}</p>`;
                                alert("Error uploading " + file.name + "! Status: " + xhr.status);
                                reject(new Error(`Upload failed with status ${xhr.status}`));
                            }
                        };

                        xhr.onerror = function() {
                            uploadStatusDiv.innerHTML = `<p class="error">Network error during upload of <strong>${file.name}</strong>.</p>`;
                            alert("Network error during upload of " + file.name);
                            reject(new Error("Network error"));
                        };

                        xhr.send(formData);
                    });
                } catch (error) {
                    console.error("Upload process error:", error);
                    // Continue to next file even if one fails, or break if desired
                }
            }

            // After all files attempt to upload
            if (uploadedFilesCount === totalFiles) {
                uploadStatusDiv.innerHTML = `<p style="color: var(--primary-color);">All files uploaded successfully!</p>`;
            } else if (uploadedFilesCount > 0) {
                uploadStatusDiv.innerHTML = `<p class="error">Finished with ${uploadedFilesCount} of ${totalFiles} files uploaded successfully.</p>`;
            } else {
                 uploadStatusDiv.innerHTML = `<p class="error">No files were uploaded successfully.</p>`;
            }
            
            // Give a moment for the user to see the final status before reloading
            setTimeout(() => {
                window.location.reload(); 
            }, 1500); // Reload after 1.5 seconds
        });
    </script>

</body>
</html>
'''

@app.route("/", methods=["GET", "POST"])
def index():
    if request.method == "POST":
        if 'file' in request.files:
            file = request.files['file']
            if file.filename == '':
                flash("No file selected for upload.", "error")
                return '', 400 

            filename = secure_filename(file.filename)
            file_path = os.path.join(app.config['SHARE_FOLDER'], filename)

            try:
                if not os.path.exists(file_path):
                    file.save(file_path)
                    flash(f"File '{filename}' uploaded successfully.", "success")
                else:
                    flash(f"File '{filename}' already exists on the server.", "warning")
                return '', 200  # Success
            except Exception as e:
                flash(f"Server error saving '{filename}': {e}", "error")
                return '', 500 # Internal Server Error
        else:
            flash("No file part in the request.", "error")
            return '', 400

    files = os.listdir(app.config['SHARE_FOLDER'])
    files.sort() 
    return render_template_string(html_template, files=files)

@app.route('/uploads/<filename>')
def uploaded_file(filename):
    try:
        return send_from_directory(app.config['SHARE_FOLDER'], filename)
    except FileNotFoundError:
        flash("File not found.", "error")
        return redirect(url_for('index'))

@app.route('/clean', methods=["POST"])
def clean():
    success_count = 0
    error_count = 0
    for filename in os.listdir(app.config['SHARE_FOLDER']):
        file_path = os.path.join(app.config['SHARE_FOLDER'], filename)
        try:
            os.remove(file_path)
            success_count += 1
        except Exception as e:
            print(f"Error removing file {file_path}: {e}")
            error_count += 1
    
    if success_count > 0 and error_count == 0:
        flash(f"All {success_count} files have been successfully deleted.", "success")
    elif success_count > 0 and error_count > 0:
        flash(f"Deleted {success_count} files, but encountered errors with {error_count} files.", "warning")
    elif error_count > 0:
        flash("Could not delete any files due to errors.", "error")
    else:
        flash("No files to delete.", "info")

    return redirect(url_for('index'))

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5002, debug=True)