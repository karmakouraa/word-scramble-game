WordScramble — Multiplayer Word Game
A real-time multiplayer word scramble game built with Java (server/networking) and JavaFX (client UI). Players compete to unscramble themed words, earn points, and use power-ups within a 60-second round.

Features

Real-time multiplayer — up to 4 players per session over TCP sockets
Themed word rounds — words are pulled from a database by theme (e.g. "java")
Scoring system — points awarded per word based on length/difficulty
Power-ups — Extra Time (+15 seconds) and Hints (up to 2 per player)
Live chat — in-game messaging between players
Animated UI — JavaFX letter tiles with pulse animations
Persistent scores — results saved to a database per session


Project Structure
├── GameServer.java       # TCP server — manages rounds, players, scoring
├── ClientHandler.java    # Per-client socket thread on the server side
├── GameClient.java       # JavaFX client — connects to server, renders UI
├── LetterTile.java       # JavaFX component — animated letter tile widget
└── wordscramble/
    ├── model/
    │   ├── GameMessage.java   # Serializable message passed over sockets
    │   └── Player.java        # Player state (score, power-up flags)
    ├── db/
    │   └── DatabaseManager.java  # Word/score persistence
    └── util/
        └── WordUtils.java     # Word scrambling, validation, hint generation

Requirements

Java 17+
JavaFX 17+ (for the client)
A running database compatible with DatabaseManager (configure connection there)


Getting Started
1. Compile
bashjavac --module-path /path/to/javafx/lib --add-modules javafx.controls \
  GameServer.java ClientHandler.java GameClient.java LetterTile.java \
  wordscramble/**/*.java
2. Start the Server
bashjava GameServer
The server listens on port 12345 by default. A round starts automatically once at least 2 players connect.
3. Launch a Client
bashjava --module-path /path/to/javafx/lib --add-modules javafx.controls GameClient
Repeat for each player (up to 4). Enter your player name when prompted.

Gameplay

When 2+ players join, a round begins — a scrambled word and theme are shown.
Players have 60 seconds to submit valid unscrambled words.
Each correct word earns points and adds +5 seconds to the timer.
Power-ups (one-time use per player):

Extra Time — adds 15 seconds to the round clock.
Hint — reveals a clue about the current word (max 2 per player).


The round ends when time runs out or too few players remain.
Final scores are broadcast and saved to the database.


Configuration
ConstantLocationDefaultDescriptionPORTGameServer.java12345TCP port the server binds toROUND_SECONDSGameServer.java60Duration of each roundMAX_PLAYERSGameServer.java4Maximum concurrent players

Message Protocol
Client and server communicate via serialized GameMessage objects over TCP. Key message types:
TypeDirectionDescriptionJOINClient → ServerRegister player nameROUND_STARTServer → ClientScrambled word, theme, timeSUBMIT_WORDClient → ServerPlayer's word guessWORD_RESULTServer → ClientVALID or INVALID + reasonSCORE_UPDATEServer → AllUpdated leaderboardPOWERUP_REQUESTClient → ServerRequest a power-upPOWERUP_GRANTServer → ClientPower-up confirmed + effectCHATBothIn-game chat messageROUND_ENDServer → AllAnswer reveal + scoresGAME_OVERServer → AllFinal results
