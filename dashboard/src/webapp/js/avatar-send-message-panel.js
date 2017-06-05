window.onload = function() {
    document.getElementById("avatarCommunicationButton").onclick = function(evt) {
        const inputField = document.getElementById("avatarCommunicationInput");
        const idInputField = document.getElementById("avatarCommunicationIdInput");

        const xhr = new XMLHttpRequest();
        const url = "http://localhost:5000/parse";
        xhr.open("POST", url, true);
        xhr.setRequestHeader("Content-type", "application/json");

        xhr.onreadystatechange = function () {
            if (xhr.readyState === 4 && xhr.status === 200) {
                const response = JSON.parse(xhr.responseText);
                const command = response.entities[0].entity;
                console.log("Received form rasa: " + xhr.responseText + " sending " + command + " to robot");
                avatarsWs.send(JSON.stringify({
                    id: idInputField.textContent,
                    message: command,
                    $type: "dashboard.clients.ServerClient.MessageToAvatar"
                }));
            }
        };

        const data = JSON.stringify({"q": inputField.textContent});
        xhr.send(data);
    };
};
