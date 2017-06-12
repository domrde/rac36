let testInput = "robot number 2 run to robot number 1 and turn on 90 degrees";

let testResponse =
    {
      "entities": [
          {
              "entity": "#1"
          },
          {
              "entity": "moveto"
          },
          {
              "entity": "#2"
          },
          {
              "entity": "and"
          },
          {
              "entity": "stop"
          },
          {
              "entity": "and"
          },
          {
              "entity": "left"
          },
          {
              "entity": "130"
          }
      ]
    };

let useTestData = true;

window.onload = function() {
    const inputField = document.getElementById("avatarCommunicationInput");
    const url = "http://localhost:5000/parse";

    if (useTestData) {
        inputField.textContent = testInput;
    }

    document.getElementById("avatarCommunicationButton").onclick = function() {
        if (useTestData) {
            sendCommands(parseResponse(testResponse));
        } else {
            const xhr = new XMLHttpRequest();
            xhr.open("POST", url, true);
            xhr.setRequestHeader("Content-type", "application/json");

            xhr.onreadystatechange = function () {
                if (xhr.readyState === 4 && xhr.status === 200) {
                    sendCommands(parseResponse(JSON.parse(xhr.responseText)));
                }
            };

            const data = JSON.stringify({"q": inputField.textContent});
            xhr.send(data);
        }
    };

    function parseResponse(responseText) {
        const targetRobotId = responseText.entities[0];

        const asStrings = _.chain(responseText.entities.slice(1)).map(function (entity) {
            return entity.entity
        }).toArray().value().join(";").split(";and;");

        return _.map(asStrings, function (group) {
            console.log(asStrings + " -> " + group);
            return {
                "targetId": targetRobotId,
                "command": group
            }
        });
    }

    function sendCommands(commands) {
        _.each(commands, function(targetId, command) {
            console.log("Sending " + command + " to robot " + targetId);
            avatarsWs.send(JSON.stringify({
                id: targetId,
                message: command,
                $type: "dashboard.clients.ServerClient.MessageToAvatar"
            }));
        });
    }
};
