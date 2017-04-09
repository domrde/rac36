const avatarsWs = new WebSocket("ws://" + window.location.host + "/avatar");
let avatarsPanelBody = null;

avatarsWs.onopen = function (evt) {
    avatarsPanelBody = document.getElementById("avatarsPanelBody");
};

avatarsWs.onmessage = function (evt) {
    const parsedData = JSON.parse(evt.data);
    switch (parsedData.$type) {
        case "dashboard.clients.ServerClient.AvatarsStatuses":
            avatarsPanelBody.innerHTML = "";
            parsedData.statuses.forEach(function (item) {
                drawAvatarPanel(avatarsPanelBody, item)
            });
            break;

        default:
            console.log(parsedData);
    }

    //перерисовка одной плитки с полем адрес
    // $(".js-container").find("[data-address='" + address + "']").empty().append(INFOtemplate.render(info))
};

function drawAvatarPanel(panel, info) {
    const compiled = _.template(`
        <div class="panel-body vm-avatar-panel">
            Id: <%= id %>
            </br>
            Tunnel connection:
            <span class="glyphicon glyphicon-<%connectedToTunnel ? print("remove") : print("ok")%>-sign" aria-hidden="true"></span>
            </br>
            Brain: <%= brainStarted %>
            </br>
            <div class="btn-group" role="group" aria-label="...">
              <button type="button" class="btn btn-default" onclick="stopAvatar('<%=id%>')">Stop</button>
              <button type="button" class="btn btn-default" onclick="startAvatar('<%=id%>')">Run</button>
            </div>
        </div>
    `);
    const base = document.createElement("div");
    base.setAttribute("class", "panel panel-default little-panel pull-left");
    base.innerHTML = compiled(info);
    panel.appendChild(base);
}

function startAvatar(id) {
    avatarsWs.send(JSON.stringify({id: id, newState: "Start", $type: "dashboard.clients.ServerClient.ChangeAvatarState"}));
}

function stopAvatar(id) {
    avatarsWs.send(JSON.stringify({id: id, newState: "Stop", $type: "dashboard.clients.ServerClient.ChangeAvatarState"}));
}
