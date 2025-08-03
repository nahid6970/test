function openAddShowModal() {
    document.getElementById('addShowModal').style.display = 'block';
}

function closeAddShowModal() {
    document.getElementById('addShowModal').style.display = 'none';
}

async function openEditShowModal(showId) {
    const response = await fetch(`/edit_show/${showId}`);
    const show = await response.json();

    document.getElementById('editShowId').value = show.id;
    document.getElementById('editShowTitle').value = show.title;
    document.getElementById('editShowYear').value = show.year;
    document.getElementById('editShowCoverImage').value = show.cover_image;
    document.getElementById('editShowDirectoryPath').value = show.directory_path || '';
    document.getElementById('editShowForm').action = `/edit_show/${show.id}`;

    document.getElementById('editShowModal').style.display = 'block';
}

function closeEditShowModal() {
    document.getElementById('editShowModal').style.display = 'none';
}

async function openEditEpisodeModal(showId, episodeId) {
    const response = await fetch(`/edit_episode/${showId}/${episodeId}`);
    const episode = await response.json();

    document.getElementById('editEpisodeShowId').value = showId;
    document.getElementById('editEpisodeId').value = episode.id;
    document.getElementById('editEpisodeTitle').value = episode.title;
    document.getElementById('editEpisodeForm').action = `/edit_episode/${showId}/${episode.id}`;

    document.getElementById('editEpisodeModal').style.display = 'block';
}

function closeEditEpisodeModal() {
    document.getElementById('editEpisodeModal').style.display = 'none';
}

// Close modal if user clicks outside of it
window.onclick = function(event) {
    const addModal = document.getElementById('addShowModal');
    const editShowModal = document.getElementById('editShowModal');
    const editEpisodeModal = document.getElementById('editEpisodeModal');

    if (event.target == addModal) {
        addModal.style.display = 'none';
    } else if (event.target == editShowModal) {
        editShowModal.style.display = 'none';
    } else if (event.target == editEpisodeModal) {
        editEpisodeModal.style.display = 'none';
    }
}