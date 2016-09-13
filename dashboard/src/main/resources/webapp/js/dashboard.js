var ws = new WebSocket("ws://" + window.location.host + "/ws");
var mainPanel = null;

ws.onopen = function (evt) {
    mainPanel = document.getElementById("mainPanelBody");
};

function addrString(address) {
    return address.protocol + "://" + address.system + "@" + address.host + ":" + address.port;
}

ws.onmessage = function (evt) {
    var parsedData = JSON.parse(evt.data);
    switch (parsedData.t) {
        case "CollectedMetrics":
            console.log("CollectedMetrics: " + JSON.stringify(parsedData));
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

// заменить все, что ниже шаблоном на ejs или underscore
function drawPanel(panel, info) {
    var infoString = addrString(info.address) + " ROLE: " + info.role + " STATUS: " + info.status;

    var panelBody = document.createElement('div');
    panelBody.appendChild(document.createTextNode(infoString));

    //"panel-body vm-" + info.role.tolower + "-panel"
    var colorAttr = document.createAttribute("class");
    switch (info.role) {
        case "Dashboard":
            colorAttr.value = "panel-body vm-dashboard-panel";
            break;
        case "Avatar":
            colorAttr.value = "panel-body vm-avatar-panel";
            break;
        case "Pipe":
            colorAttr.value = "panel-body vm-pipe-panel";
            break;
        default:
            colorAttr.value = "panel-body vm-unknown";
    }
    panelBody.setAttributeNode(colorAttr);

    var base = document.createElement('div');
    var baseAttr = document.createAttribute("class");
    baseAttr.value = "panel panel-default little-panel pull-left";
    base.appendChild(panelBody);
    base.setAttributeNode(baseAttr);

    addProgressBar(panelBody, "CPU: ", info.cpuCur, info.cpuMax);
    addProgressBar(panelBody, "RAM: ", info.memCur, info.memMax);

    panel.appendChild(base);
}

function addProgressBar(parent, text, lower, upper) {
    var progress = document.createElement('div');

    var progressAttr = document.createAttribute("class");
    progressAttr.value = "progress";
    progress.setAttributeNode(progressAttr);

    var bar = document.createElement('div');

    var barAttr = document.createAttribute("class");
    barAttr.value = "progress-bar";
    bar.setAttributeNode(barAttr);

    var roleAttr = document.createAttribute("role");
    roleAttr.value = "progressbar";
    bar.setAttributeNode(roleAttr);

    var valNow = document.createAttribute("aria-valuenow");
    valNow.value = lower;
    bar.setAttributeNode(valNow);

    var valMin = document.createAttribute("aria-valuemin");
    valMin.value = "0";
    bar.setAttributeNode(valMin);

    var valMax = document.createAttribute("aria-valuemax");
    valMax.value = upper;
    bar.setAttributeNode(valMax);

    var width = document.createAttribute("style");
    width.value = "width: " + Math.round(lower / upper * 100) + "%;";
    bar.setAttributeNode(width);

    progress.appendChild(bar);

    var span = document.createElement('span');
    span.appendChild(document.createTextNode(text + lower + "/" + upper));
    progress.appendChild(span);

    parent.appendChild(progress);
}

function launchVm(image) {
    document.getElementById(image).disabled = true;
    ws.send(JSON.stringify({t: "Launch", role: image}));
}