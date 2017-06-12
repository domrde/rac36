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
        console.log("Response: " + responseText);
        const targetRobotId = response.entities[0];
        const grouped = groupInChunksByN(response.entities);

        return _.map(grouped, function (group) {
            return {
                "targetId": targetRobotId,
                "command": group[1] + ";" + group[2]
            }
        });
    }

    function groupInChunksByN(n, array) {
        return _.chain(array).groupBy(function(element, index) {
            return Math.floor(index/n);
        }).toArray().value();
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
