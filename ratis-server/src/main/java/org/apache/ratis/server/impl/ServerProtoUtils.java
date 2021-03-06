/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ratis.server.impl;

import org.apache.ratis.client.impl.ClientProtoUtils;
import org.apache.ratis.proto.RaftProtos.*;
import org.apache.ratis.proto.RaftProtos.AppendEntriesReplyProto.AppendResult;
import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.RaftClientRequest;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.protocol.TermIndex;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.apache.ratis.util.Preconditions;
import org.apache.ratis.util.ProtoUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.ratis.server.impl.RaftServerConstants.DEFAULT_CALLID;

/** Server proto utilities for internal use. */
public interface ServerProtoUtils {
  static TermIndex toTermIndex(TermIndexProto p) {
    return p == null? null: TermIndex.newTermIndex(p.getTerm(), p.getIndex());
  }

  static TermIndexProto toTermIndexProto(TermIndex ti) {
    return ti == null? null: TermIndexProto.newBuilder()
        .setTerm(ti.getTerm())
        .setIndex(ti.getIndex())
        .build();
  }

  static TermIndex toTermIndex(LogEntryProto entry) {
    return entry == null ? null :
        TermIndex.newTermIndex(entry.getTerm(), entry.getIndex());
  }

  static String toTermIndexString(LogEntryProto entry) {
    return TermIndex.toString(entry.getTerm(), entry.getIndex());
  }

  static String toTermIndexString(TermIndexProto proto) {
    return TermIndex.toString(proto.getTerm(), proto.getIndex());
  }

  static String toLogEntryString(LogEntryProto entry) {
    if (entry == null) {
      return null;
    }
    final String s;
    if (entry.hasStateMachineLogEntry()) {
      final StateMachineLogEntryProto smLog = entry.getStateMachineLogEntry();
      final ByteString clientId = smLog.getClientId();
      s = ", " + (clientId.isEmpty()? "<empty clientId>": ClientId.valueOf(clientId)) + ", cid=" + smLog.getCallId();
    } else if (entry.hasMetadataEntry()) {
      final MetadataProto metadata = entry.getMetadataEntry();
      s = "(c" + metadata.getCommitIndex() + ")";
    } else {
      s = "";
    }
    return toTermIndexString(entry) + ", " + entry.getLogEntryBodyCase() + s;
  }

  static String toString(LogEntryProto... entries) {
    return entries == null? "null"
        : entries.length == 0 ? "[]"
        : entries.length == 1? toLogEntryString(entries[0])
        : "" + Arrays.stream(entries).map(ServerProtoUtils::toLogEntryString)
            .collect(Collectors.toList());
  }
  static String toShortString(List<LogEntryProto> entries) {
    return entries.size() == 0? "<empty>"
        : "size=" + entries.size() + ", first=" + toLogEntryString(entries.get(0));
  }
  static String toString(AppendEntriesRequestProto proto) {
    return ProtoUtils.toString(proto.getServerRequest()) + "-t" + proto.getLeaderTerm()
        + ", previous=" + toTermIndexString(proto.getPreviousLog())
        + ", leaderCommit=" + proto.getLeaderCommit()
        + ", initializing? " + proto.getInitializing()
        + ", entries: " + toShortString(proto.getEntriesList());
  }
  static String toString(AppendEntriesReplyProto reply) {
    return toString(reply.getServerReply()) + "," + reply.getResult()
        + ",nextIndex:" + reply.getNextIndex() + ",term:" + reply.getTerm()
        + ",followerCommit:" + reply.getFollowerCommit();
  }

  static String toString(RequestVoteReplyProto proto) {
    return toString(proto.getServerReply()) + "-t" + proto.getTerm();
  }

  static String toString(RaftRpcReplyProto reply) {
    return reply.getRequestorId().toStringUtf8() + "->"
        + reply.getReplyId().toStringUtf8() + "," + reply.getSuccess();
  }

  static RaftConfigurationProto.Builder toRaftConfigurationProto(RaftConfiguration conf) {
    return RaftConfigurationProto.newBuilder()
        .addAllPeers(ProtoUtils.toRaftPeerProtos(conf.getPeersInConf()))
        .addAllOldPeers(ProtoUtils.toRaftPeerProtos(conf.getPeersInOldConf()));
  }

  static RaftConfiguration toRaftConfiguration(LogEntryProto entry) {
    Preconditions.assertTrue(entry.hasConfigurationEntry());
    final RaftConfigurationProto proto = entry.getConfigurationEntry();
    final RaftConfiguration.Builder b = RaftConfiguration.newBuilder()
        .setConf(ProtoUtils.toRaftPeers(proto.getPeersList()))
        .setLogEntryIndex(entry.getIndex());
    if (proto.getOldPeersCount() > 0) {
      b.setOldConf(ProtoUtils.toRaftPeers(proto.getOldPeersList()));
    }
    return b.build();
  }

  static LogEntryProto toLogEntryProto(RaftConfiguration conf, long term, long index) {
    return LogEntryProto.newBuilder()
        .setTerm(term)
        .setIndex(index)
        .setConfigurationEntry(toRaftConfigurationProto(conf))
        .build();
  }

  static LogEntryProto toLogEntryProto(StateMachineLogEntryProto smLog, long term, long index) {
    return LogEntryProto.newBuilder()
        .setTerm(term)
        .setIndex(index)
        .setStateMachineLogEntry(smLog)
        .build();
  }

  static LogEntryProto toLogEntryProto(long commitIndex, long term, long index) {
    return LogEntryProto.newBuilder()
        .setTerm(term)
        .setIndex(index)
        .setMetadataEntry(toMetadataEntryBuilder(commitIndex))
        .build();
  }

  static MetadataProto.Builder toMetadataEntryBuilder(long commitIndex) {
    return MetadataProto.newBuilder().setCommitIndex(commitIndex);
  }

  static StateMachineEntryProto.Builder toStateMachineEntryProtoBuilder(ByteString stateMachineData) {
    return StateMachineEntryProto.newBuilder().setStateMachineData(stateMachineData);
  }

  static StateMachineEntryProto.Builder toStateMachineEntryProtoBuilder(int logEntryProtoSerializedSize) {
    return StateMachineEntryProto.newBuilder().setLogEntryProtoSerializedSize(logEntryProtoSerializedSize);
  }

  static StateMachineLogEntryProto toStateMachineLogEntryProto(
      RaftClientRequest request, ByteString logData, ByteString stateMachineData) {
    if (logData == null) {
      logData = request.getMessage().getContent();
    }
    return toStateMachineLogEntryProto(request.getClientId(), request.getCallId(), logData, stateMachineData);
  }

  static StateMachineLogEntryProto toStateMachineLogEntryProto(
      ClientId clientId, long callId, ByteString logData, ByteString stateMachineData) {
    final StateMachineLogEntryProto.Builder b = StateMachineLogEntryProto.newBuilder()
        .setClientId(clientId.toByteString())
        .setCallId(callId)
        .setLogData(logData);
    if (stateMachineData != null) {
      b.setStateMachineEntry(toStateMachineEntryProtoBuilder(stateMachineData));
    }
    return b.build();
  }

  static Optional<StateMachineEntryProto> getStateMachineEntry(LogEntryProto entry) {
    return Optional.of(entry)
        .filter(LogEntryProto::hasStateMachineLogEntry)
        .map(LogEntryProto::getStateMachineLogEntry)
        .filter(StateMachineLogEntryProto::hasStateMachineEntry)
        .map(StateMachineLogEntryProto::getStateMachineEntry);
  }

  static Optional<ByteString> getStateMachineData(LogEntryProto entry) {
    return getStateMachineEntry(entry)
        .map(StateMachineEntryProto::getStateMachineData);
  }

  static boolean shouldReadStateMachineData(LogEntryProto entry) {
    return getStateMachineData(entry).map(ByteString::isEmpty).orElse(false);
  }

  /**
   * If the given entry has state machine log entry and it has state machine data,
   * build a new entry without the state machine data.
   *
   * @return a new entry without the state machine data if the given has state machine data;
   *         otherwise, return the given entry.
   */
  static LogEntryProto removeStateMachineData(LogEntryProto entry) {
    return getStateMachineData(entry)
        .filter(stateMachineData -> !stateMachineData.isEmpty())
        .map(_dummy -> rebuildLogEntryProto(entry, toStateMachineEntryProtoBuilder(entry.getSerializedSize())))
        .orElse(entry);
  }

  static LogEntryProto rebuildLogEntryProto(LogEntryProto entry, StateMachineEntryProto.Builder smEntry) {
    return LogEntryProto.newBuilder(entry).setStateMachineLogEntry(
        StateMachineLogEntryProto.newBuilder(entry.getStateMachineLogEntry()).setStateMachineEntry(smEntry)
    ).build();
  }

  /**
   * Return a new log entry based on the input log entry with stateMachineData added.
   * @param stateMachineData - state machine data to be added
   * @param entry - log entry to which stateMachineData needs to be added
   * @return LogEntryProto with stateMachineData added
   */
  static LogEntryProto addStateMachineData(ByteString stateMachineData, LogEntryProto entry) {
    Preconditions.assertTrue(shouldReadStateMachineData(entry),
        () -> "Failed to addStateMachineData to " + entry + " since shouldReadStateMachineData is false.");
    return rebuildLogEntryProto(entry, toStateMachineEntryProtoBuilder(stateMachineData));
  }

  static int getSerializedSize(LogEntryProto entry) {
    return getStateMachineEntry(entry)
        .filter(smEnty -> smEnty.getStateMachineData().isEmpty())
        .map(StateMachineEntryProto::getLogEntryProtoSerializedSize)
        .orElseGet(entry::getSerializedSize);
  }

  static RaftRpcReplyProto.Builder toRaftRpcReplyProtoBuilder(
      RaftPeerId requestorId, RaftPeerId replyId, RaftGroupId groupId, boolean success) {
    return ClientProtoUtils.toRaftRpcReplyProtoBuilder(
        requestorId.toByteString(), replyId.toByteString(), groupId, DEFAULT_CALLID, success);
  }

  static RequestVoteReplyProto toRequestVoteReplyProto(
      RaftPeerId requestorId, RaftPeerId replyId, RaftGroupId groupId,
      boolean success, long term, boolean shouldShutdown) {
    return RequestVoteReplyProto.newBuilder()
        .setServerReply(toRaftRpcReplyProtoBuilder(requestorId, replyId, groupId, success))
        .setTerm(term)
        .setShouldShutdown(shouldShutdown)
        .build();
  }

  static RaftRpcRequestProto.Builder toRaftRpcRequestProtoBuilder(
      RaftPeerId requestorId, RaftPeerId replyId, RaftGroupId groupId) {
    return ClientProtoUtils.toRaftRpcRequestProtoBuilder(
        requestorId.toByteString(), replyId.toByteString(), groupId, DEFAULT_CALLID, null);
  }

  static RequestVoteRequestProto toRequestVoteRequestProto(
      RaftPeerId requestorId, RaftPeerId replyId, RaftGroupId groupId, long term, TermIndex lastEntry) {
    final RequestVoteRequestProto.Builder b = RequestVoteRequestProto.newBuilder()
        .setServerRequest(toRaftRpcRequestProtoBuilder(requestorId, replyId, groupId))
        .setCandidateTerm(term);
    if (lastEntry != null) {
      b.setCandidateLastEntry(toTermIndexProto(lastEntry));
    }
    return b.build();
  }

  static InstallSnapshotReplyProto toInstallSnapshotReplyProto(
      RaftPeerId requestorId, RaftPeerId replyId, RaftGroupId groupId,
      long currentTerm, int requestIndex, InstallSnapshotResult result) {
    final RaftRpcReplyProto.Builder rb = toRaftRpcReplyProtoBuilder(requestorId,
        replyId, groupId, result == InstallSnapshotResult.SUCCESS);
    final InstallSnapshotReplyProto.Builder builder = InstallSnapshotReplyProto
        .newBuilder().setServerReply(rb).setTerm(currentTerm).setResult(result)
        .setRequestIndex(requestIndex);
    return builder.build();
  }

  static InstallSnapshotReplyProto toInstallSnapshotReplyProto(
      RaftPeerId requestorId, RaftPeerId replyId, RaftGroupId groupId,
      long currentTerm, InstallSnapshotResult result, long installedSnapshotIndex) {
    final RaftRpcReplyProto.Builder rb = toRaftRpcReplyProtoBuilder(requestorId,
        replyId, groupId, result == InstallSnapshotResult.SUCCESS);
    final InstallSnapshotReplyProto.Builder builder = InstallSnapshotReplyProto
        .newBuilder().setServerReply(rb).setTerm(currentTerm).setResult(result);
    if (installedSnapshotIndex > 0) {
      builder.setSnapshotIndex(installedSnapshotIndex);
    }
    return builder.build();
  }

  static InstallSnapshotReplyProto toInstallSnapshotReplyProto(
      RaftPeerId requestorId, RaftPeerId replyId, RaftGroupId groupId,
      InstallSnapshotResult result) {
    final RaftRpcReplyProto.Builder rb = toRaftRpcReplyProtoBuilder(requestorId,
        replyId, groupId, result == InstallSnapshotResult.SUCCESS);
    final InstallSnapshotReplyProto.Builder builder = InstallSnapshotReplyProto
        .newBuilder().setServerReply(rb).setResult(result);
    return builder.build();
  }

  static InstallSnapshotRequestProto toInstallSnapshotRequestProto(
      RaftPeerId requestorId, RaftPeerId replyId, RaftGroupId groupId, String requestId, int requestIndex,
      long term, TermIndex lastTermIndex, List<FileChunkProto> chunks,
      long totalSize, boolean done) {
    final InstallSnapshotRequestProto.SnapshotChunkProto.Builder snapshotChunkProto =
        InstallSnapshotRequestProto.SnapshotChunkProto.newBuilder()
            .setRequestId(requestId)
            .setRequestIndex(requestIndex)
            .setTermIndex(toTermIndexProto(lastTermIndex))
            .addAllFileChunks(chunks)
            .setTotalSize(totalSize)
            .setDone(done);
    return InstallSnapshotRequestProto.newBuilder()
        .setServerRequest(toRaftRpcRequestProtoBuilder(requestorId, replyId, groupId))
        // .setRaftConfiguration()  TODO: save and pass RaftConfiguration
        .setLeaderTerm(term)
        .setSnapshotChunk(snapshotChunkProto)
        .build();
  }

  static InstallSnapshotRequestProto toInstallSnapshotRequestProto(
      RaftPeerId requestorId, RaftPeerId replyId, RaftGroupId groupId,
      long leaderTerm, TermIndex firstAvailable) {
    final InstallSnapshotRequestProto.NotificationProto.Builder notificationProto =
        InstallSnapshotRequestProto.NotificationProto.newBuilder()
            .setFirstAvailableTermIndex(toTermIndexProto(firstAvailable));
    return InstallSnapshotRequestProto.newBuilder()
        .setServerRequest(toRaftRpcRequestProtoBuilder(requestorId, replyId, groupId))
        // .setRaftConfiguration()  TODO: save and pass RaftConfiguration
        .setLeaderTerm(leaderTerm)
        .setNotification(notificationProto)
        .build();
  }

  static AppendEntriesReplyProto toAppendEntriesReplyProto(
      RaftPeerId requestorId, RaftPeerId replyId, RaftGroupId groupId, long term,
      long followerCommit, long nextIndex, AppendResult result, long callId) {
    RaftRpcReplyProto.Builder rpcReply = toRaftRpcReplyProtoBuilder(
        requestorId, replyId, groupId, result == AppendResult.SUCCESS)
        .setCallId(callId);
    return AppendEntriesReplyProto.newBuilder()
        .setServerReply(rpcReply)
        .setTerm(term)
        .setNextIndex(nextIndex)
        .setFollowerCommit(followerCommit)
        .setResult(result).build();
  }

  static AppendEntriesRequestProto toAppendEntriesRequestProto(
      RaftPeerId requestorId, RaftPeerId replyId, RaftGroupId groupId, long leaderTerm,
      List<LogEntryProto> entries, long leaderCommit, boolean initializing,
      TermIndex previous, Collection<CommitInfoProto> commitInfos, long callId) {
    RaftRpcRequestProto.Builder rpcRequest = toRaftRpcRequestProtoBuilder(requestorId, replyId, groupId)
        .setCallId(callId);
    final AppendEntriesRequestProto.Builder b = AppendEntriesRequestProto
        .newBuilder()
        .setServerRequest(rpcRequest)
        .setLeaderTerm(leaderTerm)
        .setLeaderCommit(leaderCommit)
        .setInitializing(initializing);
    if (entries != null && !entries.isEmpty()) {
      b.addAllEntries(entries);
    }

    if (previous != null) {
      b.setPreviousLog(toTermIndexProto(previous));
    }
    ProtoUtils.addCommitInfos(commitInfos, i -> b.addCommitInfos(i));
    return b.build();
  }

  static ServerRpcProto toServerRpcProto(RaftPeer peer, long delay) {
    if (peer == null) {
      // if no peer information return empty
      return ServerRpcProto.getDefaultInstance();
    }
    return ServerRpcProto.newBuilder()
        .setId(peer.getRaftPeerProto())
        .setLastRpcElapsedTimeMs(delay)
        .build();
  }
}
