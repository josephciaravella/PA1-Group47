# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

COMP-512 (Distributed Systems, McGill, Fall 2026) Programming Assignment 1: a Travel
Reservation system (flights, cars, rooms, customers) that starts as a single-process RMI
client/server and must be distributed two ways:

1. **RMI**: client → Middleware → three per-type ResourceManagers (flights/cars/rooms).
2. **TCP sockets**: same three-tier architecture, but hand-rolled sockets instead of RMI;
   the client stays blocking, but the Middleware and ResourceManagers must handle
   requests concurrently/non-blockingly.

Spec: `COMP512-p1-2026.pdf`. Setup walkthrough: `GettingStarted.pdf`. Client command
syntax: `clientUserGuide.pdf`. All working code lives under `Template/`.

## Assignment-specific constraints (read before generating code here)

- This repo currently reflects the state where the group is using AI on the **RMI**
  portion. Per the spec, that requires: max ~1 day of solo work before merging with a
  partner's independently-built (no-AI) solution, and a full export of the AI
  conversation (attached to the report, with a token-usage estimate).
- For the **TCP sockets** portion, if AI is used, the request/response
  wrapping-and-dispatch logic must be implemented as one general, reusable
  method/service shared across (nearly) all client-callable operations — not
  duplicated per command.
- The client (`Client/Client/Client.java`, `Command.java`) must not be modified for the
  RMI distribution task — all changes happen server-side (Middleware + ResourceManagers).

## Build & run commands

Build server (from `Template/Server/`):
```
make            # compiles Server/RMI, Server/Interface, Server/Common; also produces RMIInterface.jar
make clean
```

Build client (from `Template/Client/`, depends on `../Server/RMIInterface.jar`):
```
make
make clean
```

Run (from `Template/Server/`):
```
./run_rmi.sh                     # starts rmiregistry on port 1099 (background)
./run_server.sh [<rmi_name>]     # starts one RMIResourceManager, e.g. ./run_server.sh Resources
./run_servers.sh                 # tmux/ssh convenience script for launching Flights/Cars/Rooms + middleware
                                  #   across 4 machines — MACHINES array must be filled in with real hostnames first
./run_middleware.sh <flightsHost> <carsHost> <roomsHost>   # currently a stub; see below
```

Run client (from `Template/Client/`):
```
./run_client.sh [<server_hostname> [<server_rmi_name>]]   # defaults to localhost / "Resources"
```

There is no automated test suite. Verification is manual: pipe a newline-separated
sequence of commands into the client, e.g.:
```
printf 'addcustomerid,42\naddflight,1,10,100\nreserveflight,42,1\nqueryflight,1\nquit\n' \
  | java -cp ../Server/RMIInterface.jar:. Client.RMIClient localhost Resources
```
Command syntax comes from the `Command` enum (`Client/Client/Command.java`) — use
`AddCustomerID,<id>` instead of `AddCustomer` when scripting, since `AddCustomer`
returns a server-generated ID you can't know ahead of time.

## Architecture

**Transport-agnostic core, thin transport wrapper** — this split is the pattern to
preserve/extend when adding the Middleware and the TCP layer:
- `Server/Interface/IResourceManager.java` — the full RMI `Remote` contract (all
  add/delete/query/reserve/bundle/getName methods).
- `Server/Common/ResourceManager.java` — the actual business logic, implements
  `IResourceManager` with **no RMI-specific code at all**. Owns an in-memory
  `RMHashMap m_data` keyed by string (e.g. `"flight-123"`, `"customer-42"`), guarded by
  `synchronized(m_data)` in `readData`/`writeData`/`removeData`. `readData` returns a
  clone so callers can't mutate shared state directly.
- `Server/RMI/RMIResourceManager.java` — extends `ResourceManager`, adds only `main()`
  to export the object via `UnicastRemoteObject` and bind it into the `rmiregistry`
  under the name `<s_rmiPrefix><serverName>` (currently `group_47_<name>`).

A new **Middleware** class is expected at `Server/RMI/RMIMiddleware.java` (inferred from
the commented-out line in `run_middleware.sh`: `Server.RMI.RMIMiddleware $1 $2 $3`). It
should implement `IResourceManager`, look up the three per-type RMs via
`LocateRegistry.getRegistry(host)` + `.lookup(...)` (same pattern as
`Client/Client/RMIClient.connectServer`), and dispatch each interface method to the
correct RM. `ResourceManager.bundle(...)` currently just `return false;` — implementing
bundle (reserve N flights + optional car/room at one location) and deciding where
customer state lives (Middleware vs. a dedicated RM vs. replicated on each RM) are the
two open design decisions called out in the spec — customer reservation logic
(`reserveItem` in `ResourceManager.java`) currently assumes the customer and the
reservable item live in the *same* `m_data` map, so splitting customers out changes that
method's shape.

**Data model** (`Server/Common/`):
- `RMItem` (abstract, `Serializable`/`Cloneable`) → `ReservableItem` (abstract; count,
  price, reserved, location) → `Flight` / `Car` / `Room` (each just supplies a
  `getKey()` format: `"flight-<num>"`, `"car-<location>"`, `"room-<location>"`, all
  lowercased).
- `Customer` (also an `RMItem`) holds an `RMHashMap` of `ReservedItem`s (its personal
  reservation list/bill), separate from the global reservable-item map.
- `RMHashMap` is just `HashMap<String, RMItem>` with a deep `clone()` and a debug
  `dump()`.

**Client** (`Client/Client/`): `Client.java` is an abstract REPL (parses lines into
`Command` + args, dispatches via a big `switch`); `RMIClient.java` supplies
`connectServer()` via RMI lookup with the same `s_rmiPrefix` convention as the server
side. Any new transport (TCP) needs an equivalent `Client` subclass, not a
`Client.java` rewrite.

## Known gotchas

- The template's `Client/Client/Client.java` originally contained a stray literal
  control byte (`0x03`) embedded in a line around `arguments.elementAt(1)`, causing
  `illegal character: '\u0003'` on `javac`. Already fixed in this checkout; if working
  from a fresh extraction of `Template.tar.gz`, watch for the same error.
- `s_rmiPrefix` is duplicated in both `Server/Server/RMI/RMIResourceManager.java` and
  `Client/Client/RMIClient.java` and must match exactly (currently `"group_47_"`).
  Rebuild both server and client after changing it — stale `.class` files won't pick it
  up.
- Default RMI registry port is `1099`, hardcoded in `run_rmi.sh`, `RMIResourceManager.java`,
  and `RMIClient.java` — change all three together if avoiding a port conflict with
  another group on a shared machine.
