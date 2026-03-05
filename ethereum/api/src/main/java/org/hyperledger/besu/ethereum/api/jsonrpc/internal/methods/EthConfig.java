/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec.Hardfork;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestProcessorCoordinator;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Suppliers;

public class EthConfig implements JsonRpcMethod {

  private static final Supplier<ObjectMapper> mapperSupplier = Suppliers.memoize(ObjectMapper::new);

  private final BlockchainQueries blockchain;
  private final ProtocolSchedule protocolSchedule;

  public EthConfig(
      final BlockchainQueries blockchain,
      final ProtocolSchedule protocolSchedule) {
    this.blockchain = blockchain;
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_CONFIG.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    BlockHeader header = blockchain.getBlockchain().getChainHeadHeader();
    // ETC uses block-based fork activation — use block number, not timestamp
    long currentBlock = header.getNumber();
    ProtocolSpec current = protocolSchedule.getForNextBlockHeader(header, 0);
    Optional<ScheduledProtocolSpec> next = protocolSchedule.getNextProtocolSpec(currentBlock);
    Optional<ScheduledProtocolSpec> last = protocolSchedule.getLatestProtocolSpec();

    ObjectNode result = mapperSupplier.get().createObjectNode();
    ObjectNode currentNode = result.putObject("current");
    generateConfig(currentNode, current);
    if (next.isPresent()) {
      ObjectNode nextNode = result.putObject("next");
      ScheduledProtocolSpec nextSpec = next.get();
      generateConfig(nextNode, nextSpec);
      ObjectNode lastNode = result.putObject("last");
      generateConfig(lastNode, last.orElse(nextSpec));
    } else {
      result.putNull("next");
      result.putNull("last");
    }

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), result);
  }

  void generateConfig(final ObjectNode result, final ScheduledProtocolSpec scheduledSpec) {
    generateConfig(result, scheduledSpec.fork(), scheduledSpec.spec());
  }

  void generateConfig(final ObjectNode result, final ProtocolSpec spec) {
    generateConfig(result, protocolSchedule.hardforkFor(x -> x.spec() == spec).orElseThrow(), spec);
  }

  void generateConfig(final ObjectNode result, final Hardfork fork, final ProtocolSpec spec) {
    // ETC uses block-based fork activation
    result.put("activationBlock", "0x" + Long.toHexString(fork.milestone()));

    result.put(
        "chainId", protocolSchedule.getChainId().map(c -> "0x" + c.toString(16)).orElse(null));

    PrecompileContractRegistry registry = spec.getPrecompileContractRegistry();
    ObjectNode precompiles = result.putObject("precompiles");
    registry.getPrecompileAddresses().stream()
        .map(a -> Map.entry(registry.get(a).getName(), a.getBytes().toHexString()))
        .sorted(Entry.comparingByKey())
        .forEach(e -> precompiles.put(e.getKey(), e.getValue()));

    TreeMap<String, String> systemContracts =
        new TreeMap<>(
            spec.getRequestProcessorCoordinator()
                .map(RequestProcessorCoordinator::getContractConfigs)
                .orElse(Map.of()));
    spec.getPreExecutionProcessor()
        .getHistoryContract()
        .ifPresent(a -> systemContracts.put("historyStorage", a.getBytes().toHexString()));
    ObjectNode jsonContracts = result.putObject("systemContracts");
    systemContracts.forEach(jsonContracts::put);
  }
}
