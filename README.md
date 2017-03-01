# moisture-monitor
I made this project because of a water leak in a wall behind a big wardrobe. I only realised that there was a water leak when it became visible on the wall (some months after the start of the leak).

So I had the idea to put a moisture sensor behind the wardrobe and to have a monitoring software that will alert me if the humidity is above a threshold.

I made a [BLE moisture sensor totally wireless](https://github.com/lcor1979/moisture-monitor-sensor) and I bought a Raspberry Pi 3 that will be connected to the sensor to receive the measures.

The measures will be received by a Python script based on [Pygatt](https://github.com/peplin/pygatt) (TODO) and sent as JSON to the moisture-monitor webserver.

moisture-monitor is a Scala / Akka based project running on the Raspberry Pi which receive the measures on its HTTP server and dispatch them to Akka actors that will store and analyse them. If something goes wrong (humidity too high, sensor battery too low, ...) the software will alert a [Slack](https://slack.com) bot.

Slack bot is build on [ScalaConsultants slack-bot-core](https://github.com/ScalaConsultants/scala-slack-bot-core).

I made this project just for fun and to have a good reason to learn Scala :)
It is released under Apache 2.0 license.

It still needs some documentation, fine tuning and debugging.
Also, as I am a noob in Scala and Akka, any advice is more than welcome !
