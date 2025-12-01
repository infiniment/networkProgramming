# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Java Swing-based real-time chat application** using socket programming. It features a client-server architecture with support for multiple chat rooms, mini-games (Omok/BR31), emoji support, secret chat mode, self-destructing messages, and various slash commands.

**Tech Stack:**
- Java 17
- Gradle 8.14.3
- MySQL 8.x with HikariCP connection pooling
- Flyway for database migrations
- Swing for GUI
- Socket programming for real-time communication

## Build and Run Commands

```bash
# Build the project
./gradlew build

# Run the integrated launcher (starts server in background + opens GUI)
./gradlew run

# Run server only
./gradlew runServer

# Run client GUI only
./gradlew runClientGui

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean
```

## Architecture Overview

### Communication Protocol
- **Port:** 5959 (defined in `Constants.DEFAULT_PORT`)
- **Encoding:** UTF-8
- **Protocol:** Line-based text protocol over TCP sockets
- All communication constants are centralized in `chat.util.Constants`

### Server Architecture (`chat.server`)
- **ChatServer:** Main server socket listener, manages ClientHandler instances and game managers
- **ClientHandler:** Thread per client, handles all client communication and commands
- **RoomManager:** Manages chat room lifecycle, persistence, and participant tracking
- **Room:** Individual chat room with participant list, secret mode state, and message history
- **CommandRouter:** Routes slash commands (`/help`, `/who`, `/to`, `/@`, `/secret`, `/silent`)
- **OmokGameManager:** Handles Omok (Gomoku) game sessions between two players
- **BR31GameManager:** Handles Baskin Robbins 31 game sessions with multiple players
- **UserDirectory:** Maps nicknames to PrintWriter instances for direct messaging

### Client Architecture (`chat.client`)
- **ChatClient:** Socket connection manager with async send/receive queues
- **ChatController:** Message routing and protocol handling on client side
- **GameMessageRouter:** Routes game-specific messages to appropriate game UIs

### UI Architecture (`chat.ui`)
- **LoginFrame:** Initial login screen
- **RoomListFrame:** Displays available rooms with lock status and participant counts
- **ChatFrame:** Main chat interface with emoji picker, message bubbles, secret mode UI
- **OmokGameFrame/OmokGamePanel:** Omok game UI
- **Br31GameFrame:** Baskin Robbins 31 game UI
- **Common components:** `RoundedPanel`, `RoundedBorder`, `Colors`, `FontManager`, `UiUtils`

### Database Layer (`chat.server`)
- **ChatRoomRepository:** CRUD operations for rooms table
- **ChatMessageRepository:** Saves and loads chat messages (excludes secret/game messages)
- **MemberRepository:** User management with auto-creation on first login
- Database config loaded from `src/main/resources/application.yml`

### Protocol Messages

**Client → Server Commands:**
- `/rooms` - List all rooms
- `/room.create [name] [capacity] [lock|open] [password]` - Create room
- `/join [roomName] [password]` - Join room
- `/quit` - Leave room and disconnect
- `/bomb [seconds] [message]` - Send self-destructing message
- `/gomoku` - Start Omok game
- `/31` - Start BR31 game
- `/secret on|off` - Toggle secret chat mode
- `/to [nickname] [message]` - Whisper to user
- `/@nickname [message]` - Mention user
- `/who` - List room participants
- `/silent [message]` - Send message without notifications

**Server → Client Events:**
- `@rooms [json]` - Room list update
- `@secret:on {sid} {hostNick}` - Secret mode activated
- `@secret:off {sid} {hostNick}` - Secret mode deactivated
- `@secret:clear {sid}` - Clear secret messages
- `@secret:msg {sid} {nick}: {text}` - Secret message
- `@bomb {seconds} {nick}: {text}` - Self-destructing message
- `@game:*` - Game-related events
- `@PKG_EMOJI {code}` - Emoji message

## Key Implementation Details

### Message Flow
1. **Client sends:** Text written in `ChatFrame` → `ChatClient.sendAsync()` → outQueue thread → socket
2. **Client receives:** `ChatClient.receiveThread` → `MessageListener.onMessageReceived()` → EDT → UI update
3. **Server receives:** `ClientHandler.run()` → route to command or broadcast to room
4. **Server sends:** `Room.broadcast()` → all participants' `PrintWriter`

### Room Management
- Rooms persist in MySQL (`rooms` table) and loaded on server startup
- Rooms support password protection (lock mode)
- Room owner is tracked and only owner can delete the room
- Empty rooms are preserved (not auto-deleted)
- Recent messages loaded from DB on room join

### Secret Chat Mode
- Activated per-room by any participant via `/secret on`
- Generates unique session ID (sid)
- Messages sent with `@secret:msg` prefix
- Client stores secret messages separately keyed by sid
- On `/secret off`, client removes all messages with that sid from UI
- Secret messages are NOT saved to database

### Game Integration
- Games triggered by host via `/gomoku` or `/31`
- Server broadcasts `@game:menu` to room
- Clients show join button, send `/game.join [type]` to participate
- Game moves sent as `/game.move [args]`
- Game state managed server-side in OmokGameManager/BR31GameManager
- Game UI updates via `@game:*` protocol messages

### Emoji System
- Emoji codes defined in `EmojiRegistry` (e.g., `:doing:`, `:smile:`)
- Image files stored in `src/main/resources/images/{category}/`
- Client sends `@PKG_EMOJI {code}` when emoji selected
- Server resolves emoji path and broadcasts to room
- Client renders as image bubble in chat

### Database Schema
- **users:** Stores user nicknames and IDs
- **rooms:** Room metadata (name, capacity, locked, password, owner)
- **messages:** Chat history (room, sender, content, timestamp, TTL for bombs)
- **message_recipients:** Whisper recipient mapping (for future use)

## Development Guidelines

### When Adding New Features
1. Define protocol constants in `Constants.java` first
2. Implement server-side logic in `ClientHandler` or new manager class
3. Update `CommandRouter` if adding slash commands
4. Implement client-side handling in `ChatController` or `GameMessageRouter`
5. Update UI components in `ChatFrame` or create new frame

### When Adding Database Tables
1. Migrations would go in `src/main/resources/db/migration/` (currently not used)
2. Create repository class following pattern of `ChatRoomRepository`
3. Update `application.yml` if schema changes needed

### Debugging Tips
- Server logs show `[SERVER-LOG]` prefix with nickname and message type
- Client console shows connection status and send/receive events
- Game state logged with `[GAME-JOIN]`, `[GAME-MOVE]` prefixes
- Check `Constants` class for all protocol message formats

### Code Organization
- **Shared models:** `chat.shared.model` (RoomDto, MemberDto)
- **Utilities:** `chat.util` (Constants, JsonUtil, LoggerUtil, YamlConfig)
- **UI common:** `chat.ui.common` (Colors, borders, panels)
- **Protocol:** Client-side protocol classes in `chat.client.protocol`

## Important Configuration

### Database Setup
MySQL connection configured in `src/main/resources/application.yml`:
- Default: `localhost:3306/chatdb`
- Username: `chatuser`
- Password: `chatpass`
- HikariCP pool: max 10 connections, min 2 idle

### Server Port
Default port is **5959**. Change in `Constants.DEFAULT_PORT` if needed.

### Launcher Behavior
`chat.Launcher` checks if port 5959 is already open:
- If open: assumes server running, only opens GUI
- If closed: starts server in daemon thread, then opens GUI

This allows multiple clients on same machine without duplicate servers.
