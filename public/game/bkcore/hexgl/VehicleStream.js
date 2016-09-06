var bkcore = bkcore || {};
bkcore.hexgl = bkcore.hexgl || {};


bkcore.hexgl.VehicleStream = function()
{
    console.log(window.location.host)
    this.ws = new WebSocket("ws://" + "54.244.38.229:9000" + "/vehicleStream");
    this.timer = null;

    /*this.ws.onopen = function() {
      this.ws.send("test")
    }.bind(this)*/
}

bkcore.hexgl.VehicleStream.prototype.sendEvent = function(name, value)
{
    this.ws.send(JSON.stringify({
      type: "event",
      vehicle: window.hexGL.player,
      name: name,
      value: "" + value,
      elapsed_time: this.timer.time.elapsed,
    }));
};

bkcore.hexgl.VehicleStream.prototype.sendLocation = function(lat, lon, elevation, speed, acceleration)
{
    console.log(this.timer.time.elapsed)
    this.ws.send(JSON.stringify({
      type: "location",
      vehicle: window.hexGL.player,
      location: {
        position: {
          lat: lat,
          lon: lon,
        },
        elevation: elevation
      },
      speed: speed,
      acceleration: acceleration,
      elapsed_time: this.timer.time.elapsed
    }));
};
