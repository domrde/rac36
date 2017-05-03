const ws = new WebSocket("ws://" + window.location.host + "/stats");
let mainPanel = null;

ws.onopen = function (evt) {
    mainPanel = document.getElementById("systemsPanelBody");
};

ws.onmessage = function (evt) {
    const parsedData = JSON.parse(evt.data);
    switch (parsedData.$type) {
        case "dashboard.clients.ServerClient.CollectedMetrics":
            mainPanel.innerHTML = "";
            parsedData.metrics.forEach(function (item) {
                drawPanel(mainPanel, item)
            });
            break;

        default:
            console.log(parsedData);
    }

    //перерисовка одной плитки с полем адрес
    // $(".js-container").find("[data-address='" + address + "']").empty().append(INFOtemplate.render(info))
};


function addrString(address) {
    return address.system + "@" + address.host + ":" + address.port;
}

function drawPanel(panel, info) {
    const compiled = _.template(`
        <div class="panel-body vm-<% print(role[0].toLowerCase()); %>-panel">
            <% print(addrString(address)); %>
            </br>
            ROLE: <%= role %> STATUS:  <%= status %>
            <div class="progress">
                <div class="progress-bar" role="progressbar" aria-valuenow="<%= cpuCur %>" aria-valuemin="0" 
                     aria-valuemax="<%= cpuMax %>" style="width: <% print(Math.round(cpuCur / cpuMax * 100)); %>%;"></div>
                <span>CPU: <%= cpuCur %>/<%= cpuMax %></span>
            </div>
            <div class="progress">
                <div class="progress-bar" role="progressbar" aria-valuenow="<%= memCur %>" aria-valuemin="0"
                     aria-valuemax="<%= memMax %>" style="width: <% print(Math.round(memCur / memMax * 100));%>%;"></div>
                <span>RAM: <%= memCur %>/<%= memMax %></span>
            </div>
        </div>
    `);
    const base = document.createElement("div");
    base.setAttribute("class", "panel panel-default little-panel pull-left");
    base.innerHTML = compiled(info);
    panel.appendChild(base);
}

function launchVm(image) {
    document.getElementById(image).disabled = true;
    ws.send(JSON.stringify({
        t: "Launch",
        role: image
    }));
}