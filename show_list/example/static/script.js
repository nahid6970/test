function toggleForm() {
    var form = document.getElementById("gameForm");
    form.style.display = (form.style.display === "none" || form.style.display === "") ? "block" : "none";
}

document.addEventListener('DOMContentLoaded', function () {
    // Restore scroll position on page load
    if (sessionStorage.getItem('scrollPosition') !== null) {
        window.scrollTo(0, sessionStorage.getItem('scrollPosition'));
        sessionStorage.removeItem('scrollPosition');
    }
});

window.addEventListener('beforeunload', function () {
    // Save scroll position before refresh
    sessionStorage.setItem('scrollPosition', window.scrollY);
});

// ... existing JavaScript ...
function googleSearch() {
    const gameNameInput = document.getElementById('gameName');
    const gameName = gameNameInput.value;
    if (gameName) {
        const googleSearchUrl = `https://www.google.com/search?q=${encodeURIComponent(gameName)}`;
        window.open(googleSearchUrl, '_blank');
    } else {
        alert('Please enter a game name first.');
    }
}

function googleSearchWithYear() {
    const gameNameInput = document.getElementById('gameName');
    const gameName = gameNameInput.value;
    if (gameName) {
        const googleSearchUrl = `https://www.google.com/search?q=${encodeURIComponent(gameName)} release date`;
        window.open(googleSearchUrl, '_blank');
    } else {
        alert('Please enter a game name first.');
    }
}


let collectionData = {}; // Object to store loaded data for each game

function toggleCollection(toggleElement, collectionId, gameId, collectionName) {
    const collectionText = document.getElementById(collectionId);
    const otherGamesDiv = document.getElementById(`other-games-${gameId}`);
    toggleElement.classList.toggle('collapsed');

    const isExpanded = collectionText.style.display === 'block';

    if (!isExpanded) {
        collectionText.style.display = 'block';
        if (!collectionData[gameId]) {
            collectionData[gameId] = 'loading';
            otherGamesDiv.style.display = 'block';
            otherGamesDiv.innerHTML = 'Loading other games...';
            fetch(`/get_collection_games?game_id=${gameId}&collection_name=${encodeURIComponent(collectionName)}`)
                .then(response => response.json())
                .then(data => {
                    let html = '<ul>';
                    if (data.length > 0) {
                        data.forEach(otherGame => {
                            html += `<li style="${otherGame.style}">${otherGame.name} (${otherGame.year})</li>`;
                        });
                    } else {
                        html += '<li>No other games in this collection.</li>';
                    }
                    html += '</ul>';
                    otherGamesDiv.innerHTML = html;
                    collectionData[gameId] = data; // Store loaded data
                })
                .catch(error => {
                    console.error('Error fetching collection games:', error);
                    otherGamesDiv.innerHTML = 'Error loading other games.';
                    collectionData[gameId] = 'error';
                });
        } else if (collectionData[gameId] !== 'loading' && collectionData[gameId] !== 'error') {
            otherGamesDiv.style.display = 'block'; // Show if already loaded
        } else if (collectionData[gameId] === 'error') {
            otherGamesDiv.style.display = 'block';
            otherGamesDiv.innerHTML = 'Error loading other games.';
        }
    } else {
        collectionText.style.display = 'none';
        otherGamesDiv.style.display = 'none';
    }
}
function filterByCollection(collection) {
    window.location.href = '/?collection=' + collection;
}
