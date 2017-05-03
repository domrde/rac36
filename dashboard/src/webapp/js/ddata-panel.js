const ddataWs = new WebSocket("ws://" + window.location.host + "/ddata");
let ddataCanvas = null;
let ddataContext = null;

ddataWs.onopen = function (evt) {
    ddataCanvas = document.getElementById("ddataCanvas");
    ddataCanvas.width = 400;
    ddataCanvas.height = 400;
    ddataContext = ddataCanvas.getContext("2d");
};

ddataWs.onmessage = function (evt) {
    const parsedData = JSON.parse(evt.data);
    switch (parsedData.$type) {
        case "dashboard.clients.ServerClient.PositionsData":
            updateCanvas(parsedData.data);
            break;

        default:
            console.log(parsedData);
    }
};

Array.prototype.minBy = function (fn) {
    return this.extremumBy(fn, Math.min);
};

Array.prototype.maxBy = function (fn) {
    return this.extremumBy(fn, Math.max);
};

Array.prototype.extremumBy = function (pluck, extremum) {
    return this.reduce(function (best, next) {
        var pair = [pluck(next), next];
        if (!best) {
            return pair;
        } else if (extremum.apply(null, [best[0], pair[0]]) == best[0]) {
            return best;
        } else {
            return pair;
        }
    }, null)[1];
};

function updateCanvas(positions) {
    if (positions.length > 0) {

        const ranges = positions.reduce(function (accumulator, element) {
            if (!('minX' in accumulator) || element.x < accumulator['minX']) {
                accumulator['minX'] = element.x;
            }
            if (!('minY' in accumulator) || element.y < accumulator['minY']) {
                accumulator['minY'] = element.y;
            }
            if (!('maxX' in accumulator) || (element.x + element.width) > accumulator['maxX']) {
                accumulator['maxX'] = (element.x + element.width);
            }
            if (!('maxY' in accumulator) || (element.y + element.height) > accumulator['maxY']) {
                accumulator['maxY'] = (element.y + element.height);
            }
            return accumulator;
        }, {});

        const xAllign = function () {
            if (ranges.minX < 0) {
                return -ranges.minX;
            } else {
                return 0;
            }
        }();

        const yAllign = function () {
            if (ranges.minY < 0) {
                return -ranges.minY;
            } else {
                return 0;
            }
        }();

        const relation = (ranges.maxX - ranges.minX) / (ranges.maxY - ranges.minY);
        ddataCanvas.width = 400 * relation;
        ddataContext.fillStyle = "#fff1cc";
        ddataContext.fillRect(0, 0, ddataCanvas.width, ddataCanvas.height);

        const xMultiplier = ddataCanvas.width / (ranges.maxX - ranges.minX);
        const yMultiplier = ddataCanvas.height / (ranges.maxY - ranges.minY);

        positions.forEach(function (position) {
            if (position.name === "obstacle") {
                ddataContext.fillStyle = "red";
            } else {
                ddataContext.fillStyle = "#fbd5ad";
            }
            ddataContext.fillRect(
                (position.x + xAllign) * xMultiplier,
                (position.y + yAllign) * yMultiplier,
                position.width * xMultiplier,
                position.height * yMultiplier
            );
        });
    } else {
        ddataContext.fillStyle = "#fff1cc";
        ddataContext.fillRect(0, 0, ddataCanvas.width, ddataCanvas.height);
        ddataContext.strokeStyle = "black";
        ddataContext.strokeRect(0, 0, ddataCanvas.width, ddataCanvas.height);
    }
}