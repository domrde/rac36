var ws = new WebSocket("ws://" + window.location.host + "/ws");
var mainPanel = null;
var nodesInfo = {};

ws.onopen = function (evt) {
    mainPanel = document.getElementById("mainPanelBody");
};

function addrString(address) {
    return address.protocol + "://" + address.system + "@" + address.host + ":" + address.port;
}

function addNodeInfo(infoStorage, addr, mem, cpu, role) {
    var address = addrString(addr);
    if (address in infoStorage) {
        if (mem != null) infoStorage[address].mem = Math.round(mem);
        if (cpu != null) infoStorage[address].cpu = cpu;
        if (role != null) infoStorage[address].role = role;
    } else {
        infoStorage[address] = { address: address, mem: Math.round(mem), cpu: cpu, role: role};
    }
}

ws.onmessage = function (evt) {
    var parsedData = JSON.parse(evt.data);
    switch (parsedData.t) {
        case "ShardMetrics":
            console.log("ShardMetrics: " + JSON.stringify(parsedData));
            break;

        case "RegionMetrics":
            console.log("RegionMetrics: " + JSON.stringify(parsedData));
            break;

        case "MemoryMetrics":
            console.log("MemoryMetrics: " + JSON.stringify(parsedData));
            addNodeInfo(nodesInfo, parsedData.address, parsedData.usedHeap, null, null);
            redraw(mainPanel, nodesInfo);
            break;

        case "CpuMetrics":
            console.log("CpuMetrics: " + JSON.stringify(parsedData));
            addNodeInfo(nodesInfo, parsedData.address, null,
                parsedData.average + "/" + parsedData.processors, null);
            redraw(mainPanel, nodesInfo);
            break;

        case "NodeUp":
            console.log("NodeUp: " + JSON.stringify(parsedData));
            addNodeInfo(nodesInfo, parsedData.address, null, null, parsedData.role);
            document.getElementById(parsedData.role).disabled = false;
            redraw(mainPanel, nodesInfo);
            break;

        case "NodeDown":
            console.log("NodeDown: " + JSON.stringify(parsedData));
            delete nodesInfo[parsedData.address];
            redraw(mainPanel, nodesInfo);
            break;

        case "DdataStatus":
            console.log("DdataStatus: " + JSON.stringify(parsedData));
            break;

        default:
            console.log(parsedData);
    }
};

function redraw(panel, infoStorage) {
    panel.innerHTML = "";
    for(var index in infoStorage) {
        createPanels(infoStorage[index], panel)
    }
}

function createPanels(info, panel) {
    var infoString = info.address + "   CPU: " + info.cpu + ",  MEM: " + info.mem + "MB,  ROLE: " + info.role;

    var panelBody = document.createElement('div');
    panelBody.appendChild(document.createTextNode(infoString));

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
    baseAttr.value = "panel panel-default little-panel";
    base.appendChild(panelBody);
    base.setAttributeNode(baseAttr);

    panel.appendChild(base);
}

function launchVm(image) {
    document.getElementById(image).disabled = true;
    ws.send(JSON.stringify({t: "Launch", role: image}));
}