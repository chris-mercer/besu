# Besu — Ethereum Classic Client (Olympia Fork)

**Status:** Setup — ETC support via custom genesis files
**Language:** Java 21 LTS
**Build:** Gradle 9.3.1
**License:** Apache 2.0
**Origin:** Fork of hyperledger/besu, maintained by chris-mercer
**Branch:** `olympia` (Olympia hard fork implementation)

---

## Quick Commands

```bash
./gradlew installDist -x test   # Build (binary → build/install/besu/bin/besu)
./gradlew test                  # Unit tests
./gradlew integrationTest       # Integration tests
./gradlew spotlessApply         # Format code
```

### Run on Mordor Testnet

```bash
bash run-mordor.sh
```

### Run on ETC Mainnet

```bash
bash run-classic.sh
```

---

## Data Directories

| Network | Path |
|---------|------|
| Mordor | `/media/dev/2tb/data/blockchain/besu/mordor/` |
| ETC Mainnet | `/media/dev/2tb/data/blockchain/besu/classic/` |

---

## Network Ports (unique — can run alongside core-geth)

| Port | Protocol |
|------|----------|
| 8548 | HTTP JSON-RPC |
| 8549 | WS JSON-RPC |
| 8547 | GraphQL |
| 30304 | P2P (TCP+UDP) |

**Note:** core-geth uses 8545/8546/30303. Besu uses offset ports to allow concurrent operation.

---

## ETC Support

Besu does NOT have built-in ETC network support. ETC is configured via custom genesis files:
- `config/classic.json` — ETC mainnet (chain ID 61)
- `config/mordor.json` — Mordor testnet (chain ID 63)

Use `--genesis-file=config/<network>.json` instead of `--network=<name>`.

---

## Project Structure

```
app/                # CLI entry point (BesuCommand.java)
config/             # Genesis configs (mainnet, sepolia, classic, mordor)
consensus/          # Consensus engines (ethash, ibft, qbft)
crypto/             # Cryptographic primitives
datatypes/          # Core data types
docker/             # Container build
ethereum/           # Core protocol implementation
  ├── api/          # JSON-RPC API
  ├── core/         # Block processing, state
  ├── eth/          # Wire protocol
  ├── p2p/          # P2P networking
  ├── rlp/          # RLP encoding
  └── trie/         # Merkle Patricia Trie
evm/                # EVM implementation
gradle/             # Build configuration
```

---

## Key Files for ETC/Olympia Work

| File | Purpose |
|------|---------|
| `config/src/main/java/.../NetworkDefinition.java` | Built-in network definitions (add CLASSIC/MORDOR here) |
| `config/src/main/java/.../GenesisConfigOptions.java` | Genesis config parsing interface |
| `config/src/main/java/.../JsonGenesisConfigOptions.java` | JSON genesis parser |
| `consensus/common/src/main/java/.../EthashConfig.java` | Ethash consensus config |
| `ethereum/core/src/main/java/.../ProtocolScheduleBuilder.java` | Fork activation schedule |
| `evm/src/main/java/.../EvmSpecVersion.java` | EVM version definitions |

---

## Boundaries

### Always Do
- Run `./gradlew spotlessApply` before commits
- Use custom genesis files for ETC networks
- Test against Mordor before mainnet
- Respect consensus-critical code boundaries

### Ask First
- Changes to EVM opcodes or precompiles
- Modifying consensus engine behavior
- Docker image changes
- CI/CD workflow modifications

### Never Do
- Break backwards compatibility with ETC network protocol
- Commit private keys or mnemonics
- Remove or bypass tests
- Use `latest` tags in Docker images
