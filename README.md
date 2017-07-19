This is a project aiming to provide virtual avatars to connected clients, that share sent information and able to interact with eachother via messages. To achieve that clients are connected via ZeroMQ, their messages translated to akka-messages in PIPEs and forwarded to VIVARIUMs that run their avatars. Project is not finished and some things are definetly not working.


                 +-----------------------------------+
                 |           Akka Cluster            |
                 |                                   |
                 |                                   |
              ZeroMq                                 |
    Client  <-------->  +------+     +----------+    |
                 |      |      |     |          |    |
                 |      | PIPE <-----> VIVARIUM |    |
    Client  <-------->  |      |     |          |    |
                 |      +------+     +----------+    |
      .          |                                   |
      .          |      +------+     +----------+    |
      .          |      |      |     |          |    |
                 |      | PIPE <-----> VIVARIUM |    |
    Client  <-------->  |      |     |          |    |
                 |      +----+-+     +----+-----+    |
                 |           |            |          |
                 |           v            v          |
                 |                                   |
                 |        +-------------------+      |
                 |        |                   |      |
                 |        |     DASHBOARD     |      |
                 |        |                   |      |
                 |        +-------------------+      |
                 |                                   |
                 +-----------------------------------+

