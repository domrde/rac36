var ws = new WebSocket("ws://" + window.location.host + "/ws");
var mainList = null;
var knownInfo = {};

ws.onmessage = function (evt) {
    var parsedData = JSON.parse(evt.data);
    switch (parsedData.t) {
        case "ShardMetrics":
            console.log("ShardMetrics: " + parsedData.metrics);
            break;

        case "MemoryMetrics":
            if (addrString(parsedData.address) in knownInfo) {
                knownInfo[addrString(parsedData.address)].mem = Math.round(parsedData.usedHeap);
            } else {
                knownInfo[addrString(parsedData.address)] = { mem: Math.round(parsedData.usedHeap), cpu: null};
            }
            refillList(mainList, knownInfo);
            console.log("MemoryMetrics: " + addrString(parsedData.address) + " - " +
                parsedData.usedHeap);
            break;

        case "CpuMetrics":
            if (addrString(parsedData.address) in knownInfo) {
                knownInfo[addrString(parsedData.address)].cpu = parsedData.average + "/" + parsedData.processors;
            } else {
                knownInfo[addrString(parsedData.address)] = { mem: null, cpu: parsedData.average + "/" + parsedData.processors};
            }
            refillList(mainList, knownInfo);
            console.log("CpuMetrics: " + addrString(parsedData.address) + " - " +
                parsedData.average + "/" + parsedData.processors);
            break;

        case "DdataStatus":
            console.log("DdataStatus: " + parsedData.data);
            break;

        case "Launched":
            document.getElementById(parsedData.image).disabled = false;

        default:
            console.log(parsedData);
    }
};

function addrString(address) {
    return address.protocol + "://" + address.system + "@" + address.host + ":" + address.port;
}

function refillList(list, knownInfo) {
    list.innerHTML = "";
    for(var info in knownInfo) {
        insertCpuMemIntoList(info + "   CPU: " + knownInfo[info].cpu + "  MEM: " + knownInfo[info].mem + "MB", list)
    }
}

function insertCpuMemIntoList(cpumem, list) {
    var li = document.createElement('li');
    var attr = document.createAttribute("class");
    attr.value = "list-group-item";
    li.appendChild(document.createTextNode(cpumem));
    li.setAttributeNode(attr);
    list.appendChild(li);
}

function launchVm(image) {
    document.getElementById(image).disabled = true;
    ws.send(JSON.stringify({t: "Launch", image: image}));
}

ws.onopen = function (evt) {
    mainList = document.getElementById("mainList");
};