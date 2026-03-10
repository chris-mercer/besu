# Besu Olympia Branch — Handoff Document

## Overview

The `olympia` branch implements the Olympia hard fork for Besu — ETC's biggest protocol upgrade, bringing EIP-1559 with treasury credit, 13 Cancun/Prague EIPs, and 4 deferred EIPs (block hashes in state, TX gas cap, block size limit, default gas limit).

**Branch:** `olympia` (from `etc`)
**Parent:** `etc` (from `main`) — ETC client through Spiral. See `ETC-HANDOFF.md` on the `etc` branch.
**Commits:** 6 ahead of `etc`
**Status:** ALL COMPLETE — 14 EIPs + treasury + deferred EIPs implemented and tested
**Olympia activation:** Mordor block 15,800,850 / ETC mainnet block 24,751,337

---

## Commit History

| # | Hash | Description |
|---|------|-------------|
| 1 | `ff12bb2403` | Config layer + OlympiaBlockProcessor + ClassicEVMs.olympia() |
| 2 | `fb87092580` | Protocol spec, precompiles, factory, milestone wiring |
| 3 | `93fefb21d0` | Comprehensive Olympia test suite (35 new tests) |
| 4 | `34b19f8cc2` | Handoff documentation |
| 5 | `4eb88a2058` | Deferred EIPs: EIP-2935, EIP-7825, EIP-7934 |
| 6 | `f5eaa21930` | Deferred EIP tests + OLYMPIA-HANDOFF.md |

---

## Olympia EIP Summary

**ECIP-1111 (EIP-1559 + treasury):**
- EIP-1559: Dynamic basefee, Type-2 transactions — basefee credited to treasury (not burned)
- EIP-3198: BASEFEE opcode

**ECIP-1121 (13 EIPs):**
- EIP-5656: MCOPY | EIP-1153: TLOAD/TSTORE | EIP-6780: SELFDESTRUCT nerf
- EIP-2537: BLS12-381 precompiles | EIP-7951: P-256 precompile
- EIP-7823/7883: MODEXP bounds + gas | EIP-7825: TX gas cap 2^24
- EIP-7623: Floor calldata gas | EIP-7935: Default gas limit 60M
- EIP-2935: Block hashes in state | EIP-7702: EOA code delegation
- EIP-7934: Block size limit

**Treasury:** `0xd6165F3aF4281037bce810621F62B43077Fb0e37`

---

## Architecture

**Treasury credit is ADDITIVE:** `OlympiaBlockProcessor` extends `ClassicBlockProcessor` and overrides `rewardCoinbase()`. After computing standard ECIP-1017 era rewards, it credits `baseFee × gasUsed` to the treasury address. EIP-1559 transaction processing is not modified — Besu's standard EIP-1559 flow implicitly burns basefee (sender pays, miner gets tips only), and the treasury credit is added separately in block finalization.

**Gas calculator:** `OsakaGasCalculator` — handles all gas cost changes (BLS12-381, P-256, EIP-7883 MODEXP repricing, calldata floor). Extends `PragueGasCalculator` with overridden `modExpGasCost()` (min 500, no /3 divisor) and `isPrecompile()` (P256VERIFY at 0x0100). Blob-specific methods exist but are never invoked without blob transactions.

**EVM:** `ClassicEVMs.olympia()` — Istanbul ops + PUSH0 + BASEFEE + TLOAD/TSTORE + MCOPY + SelfDestruct(eip6780=true). No BLOBHASH, BLOBBASEFEE, PREVRANDAO.

**Precompiles:** Istanbul + BLS12-381 (7 contracts) + MODEXP(1024 bound) + P256VERIFY. No KZG point evaluation (no blobs on ETC).

**Header validator:** Custom base fee market validator using `Ecip1099EpochCalculator` (60K epochs) for PoW validation. The mainnet `createBaseFeeMarketValidator()` hardcodes `DefaultEpochCalculator` (30K), so Olympia uses `createClassicBaseFeeMarketValidator()` and `createClassicBaseFeeMarketOmmerValidator()` in `ClassicProtocolSpecs.java`.

**Transaction types:** FRONTIER + ACCESS_LIST + EIP1559 + DELEGATE_CODE (no BLOB)

---

## Deferred EIPs (Commits 5-6) — ALL COMPLETE

### EIP-2935: Block Hashes in State

System contract at `0x0000f90827f1c53a10cb7a02335b175320002935` stores parent block hashes in 8191-slot rotating storage. `OlympiaPreExecutionProcessor` extends `FrontierPreExecutionProcessor` (NOT Cancun/Prague — avoids beacon roots, ETC is PoW). Deploys contract at fork activation if missing (handles both fresh sync via genesis alloc and live network fork). `BLOCKHASH` opcode reads from contract storage via `Eip7709BlockHashLookup`.

**New file:** `blockhash/OlympiaPreExecutionProcessor.java`

### EIP-7825: Transaction Gas Cap (2^24 = 16,777,216)

`OlympiaTargetingGasLimitCalculator` extends `LondonTargetingGasLimitCalculator` with `transactionGasLimitCap() = 16_777_216L` (2^24 per final EIP-7825 spec). Besu's `MainnetTransactionValidator` already checks this cap — enforced both during block validation and txpool admission.

**New file:** `OlympiaTargetingGasLimitCalculator.java`

### EIP-7934: Block RLP Size Limit (8 MB)

`MainnetBlockValidatorBuilder.olympia()` creates a `MainnetBlockValidator` with `OLYMPIA_MAX_RLP_BLOCK_SIZE = 8_388_608`. The existing `MainnetBlockValidator` checks `block.getSize() > maxRlpBlockSize` during validation.

**Modified file:** `MainnetBlockValidatorBuilder.java`

### EIP-7623: Floor Calldata Gas

Already handled by `PragueGasCalculator` (inherited). No additional work needed.

### EIP-7935: Default Gas Limit (60M) — Miner Policy Only

NOT a consensus rule. The 60M gas limit is a recommended default for miners/validators. Configure via `--target-gas-limit=60000000` CLI flag. The `OlympiaTargetingGasLimitCalculator` handles elastic adjustment toward the target via inherited EIP-1559 logic.

---

## Files

| Action | File | Purpose |
|--------|------|---------|
| NEW | `OlympiaBlockProcessor.java` | Treasury credit: baseFee × gasUsed → treasury |
| NEW | `blockhash/OlympiaPreExecutionProcessor.java` | EIP-2935: block hash system contract + EIP-7709 lookup |
| NEW | `OlympiaTargetingGasLimitCalculator.java` | EIP-7825: 2^24 per-TX gas cap |
| NEW | `OlympiaBlockProcessorTest.java` | 12 treasury credit tests |
| NEW | `OlympiaProtocolSpecsTest.java` | 17 fork definition + deferred EIP tests |
| NEW | `OlympiaDeferredEipsTest.java` | 12 deferred EIP tests (2935, 7825, 7934, 7935) |
| NEW | `GenesisConfigOlympiaTest.java` | 12 genesis config tests (includes EIP-2935 alloc) |
| MOD | `HardforkId.java` | Added OLYMPIA to ClassicHardforkId enum |
| MOD | `GenesisConfigOptions.java` | getOlympiaBlockNumber, getOlympiaTreasuryAddress |
| MOD | `JsonGenesisConfigOptions.java` | Implementations + asMap + forkBlockNumbers |
| MOD | `StubGenesisConfigOptions.java` | Fields, getters, builders |
| MOD | `mordor.json` | olympiaBlock, treasury address, EIP-2935 contract in alloc |
| MOD | `classic.json` | olympiaBlock, treasury address, EIP-2935 contract in alloc |
| MOD | `all_forks.json` | olympiaBlock + treasury for round-trip test |
| MOD | `ClassicEVMs.java` | olympia() + olympiaOperations() |
| MOD | `ClassicProtocolSpecs.java` | olympiaDefinition() with deferred EIPs wired |
| MOD | `MainnetBlockValidatorBuilder.java` | olympia() with 8MB RLP limit (EIP-7934) |
| MOD | `MainnetPrecompiledContracts.java` | populateForOlympia() |
| MOD | `MainnetPrecompiledContractRegistries.java` | olympia() factory |
| MOD | `MainnetProtocolSpecFactory.java` | olympiaDefinition() factory |
| MOD | `MilestoneDefinitions.java` | OLYMPIA milestone entry |
| MOD | `ClassicProtocolSpecsTest.java` | +5 Olympia tests |
| MOD | `GenesisConfigClassicTest.java` | +3 Olympia tests |

---

## Test Coverage

| File | Tests | Coverage |
|------|-------|---------|
| `OlympiaBlockProcessorTest.java` | 12 | Treasury credit calculations, era reward coexistence, accumulation, edge cases |
| `OlympiaProtocolSpecsTest.java` | 17 | Fork ID, gas calc, EIP-1559, block processor, no withdrawals, preExecutionProcessor, gasLimitCalculator |
| `OlympiaDeferredEipsTest.java` | 12 | EIP-2935 (history contract, lookup), EIP-7825 (TX gas cap 2^24), EIP-7934 (block size 8MB), EIP-7935 (miner policy) |
| `GenesisConfigOlympiaTest.java` | 12 | Mordor/classic parsing, treasury address, asMap, forkBlockNumbers, stub config, EIP-2935 alloc |
| `ClassicProtocolSpecsTest.java` | +5 | Olympia fork ID, gas calc, processor, EIP-1559, no withdrawals |
| `GenesisConfigClassicTest.java` | +3 | Olympia block numbers, asMap keys, stub config |
| **Total** | **61** | |

---

## Running Tests

```bash
# All Olympia tests (61 tests)
./gradlew :ethereum:core:test --tests "*Olympia*"
./gradlew :config:test --tests "*GenesisConfigOlympia*"

# Deferred EIPs tests only (12 tests)
./gradlew :ethereum:core:test --tests "*OlympiaDeferredEips*"

# All Classic + Olympia tests (regression)
./gradlew :ethereum:core:test --tests "*Classic*" --tests "*Olympia*" --tests "*ArtificialFinality*" --tests "*EtcHash*"
./gradlew :config:test --tests "*GenesisConfig*"

# Full module regression
./gradlew :ethereum:core:test   # ~9-11 min
./gradlew :config:test           # ~11 sec
```

---

## Recommended Mining Configuration

For Olympia-era miners, set the target gas limit to 60M (EIP-7935 recommended default):

```bash
build/install/besu/bin/besu --network=CLASSIC \
  --data-path=/media/dev/2tb/data/blockchain/besu/classic \
  --target-gas-limit=60000000 \
  --miner-enabled --miner-coinbase=<address> \
  --rpc-http-enabled --rpc-http-port=8548
```

---

## Cross-Client Verification Methodology

Every EIP and ECIP in the Olympia fork is verified using a six-client cross-verification process:

**Reference Clients (ETH production):**
- [go-ethereum](https://github.com/ethereum/go-ethereum) — canonical Go implementation
- [Erigon](https://github.com/erigontech/erigon) — optimized Go implementation
- [Nethermind](https://github.com/NethermindEth/nethermind) — .NET implementation

**ETC Clients (implementation targets):**
- core-geth (`chris-mercer/core-geth`) — Go, forked from go-ethereum
- Fukuii (`chris-mercer/fukuii`) — Scala/JVM, forked from Mantis
- Besu (`chris-mercer/besu`) — Java/JVM, forked from Hyperledger Besu

**Process (per EIP/ECIP):**
1. Read the canonical EIP specification at `eips.ethereum.org`
2. Verify implementation in all 3 ETH production clients (constants, formulas, gas costs, addresses)
3. Verify implementation in all 3 ETC clients against both the spec and ETH implementations
4. Cross-compare ETC clients against each other for consistency
5. Document any discrepancies with severity (consensus-critical vs. cosmetic)

**Catches from this process:**
- **EIP-7825:** All 3 ETC clients had `MaxTxGas = 30,000,000`. ETH clients all use `1 << 24 = 16,777,216` per final spec. Corrected in all 3 ETC clients.
- **EIP-7883:** Besu wired `PragueGasCalculator` (inherits old EIP-198 `BerlinGasCalculator.modExpGasCost()`) instead of `OsakaGasCalculator` which has the correct EIP-7883 formula. Fukuii had 3 formula bugs in the same EIP. Caught by comparing against go-ethereum's `osakaModexpGas()`.
- **EIP-2537:** Core-geth and Fukuii both used the OLD 9-precompile draft (addresses 0x0a-0x12/0x13) instead of the final 7-precompile spec (0x0b-0x11). Besu was correct (inherits upstream Hyperledger Besu's Prague BLS implementation). Gas costs also diverged significantly in core-geth and Fukuii.

This methodology ensures implementation parity across all ETC clients and consistency with ETH production client behavior for shared EIPs.

---

## Reference Implementations

- core-geth: `/media/dev/2tb/dev/core-geth/` (branch `olympia`, 25 commits)
- Fukuii: `/media/dev/2tb/dev/fukuii/fukuii-client/` (branch `olympia`)
- ECIPs: `/media/dev/2tb/dev/ECIPs/_specs/`
- Treasury contract: `/media/dev/2tb/dev/olympia-treasury-contract/` (deployed on Mordor at `0xd6165F3aF4281037bce810621F62B43077Fb0e37`)
