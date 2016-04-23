var bkcore = bkcore || {};
bkcore.hexgl = bkcore.hexgl || {};

bkcore.hexgl.VehicleStream = function()
{
    this.ws = new WebSocket("ws://" + window.location.host + "/vehicleStream");
    this.ws.onopen = function() {
      this.ws.send("test")
    }.bind(this)
}

bkcore.hexgl.VehicleStream.prototype.sendEvent = function(name, value)
{
    console.log("Sending " + name + " event with value " + value);
    this.ws.send(JSON.stringify({
      vehicle: window.hexGL.player,
      name: name,
      value: "" + value
    }));
};
