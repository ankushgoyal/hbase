/**
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
package org.apache.hadoop.hbase.zookeeper;

import java.io.IOException;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.hbase.Abortable;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.protobuf.ProtobufUtil;
import org.apache.hadoop.hbase.protobuf.generated.HBaseProtos;
import org.apache.hadoop.hbase.protobuf.generated.ZooKeeperProtos;
import org.apache.zookeeper.KeeperException;

/**
 * Manages the location of the current active Master for the RegionServer.
 * <p>
 * Listens for ZooKeeper events related to the master address. The node
 * <code>/master</code> will contain the address of the current master.
 * This listener is interested in
 * <code>NodeDeleted</code> and <code>NodeCreated</code> events on
 * <code>/master</code>.
 * <p>
 * Utilizes {@link ZooKeeperNodeTracker} for zk interactions.
 * <p>
 * You can get the current master via {@link #getMasterAddress()} or via
 * {@link #getMasterAddress(ZooKeeperWatcher)} if you do not have a running
 * instance of this Tracker in your context.
 * <p>
 * This class also includes utility for interacting with the master znode, for
 * writing and reading the znode content.
 */
@InterfaceAudience.Private
public class MasterAddressTracker extends ZooKeeperNodeTracker {
  /**
   * Construct a master address listener with the specified
   * <code>zookeeper</code> reference.
   * <p>
   * This constructor does not trigger any actions, you must call methods
   * explicitly.  Normally you will just want to execute {@link #start()} to
   * begin tracking of the master address.
   *
   * @param watcher zk reference and watcher
   * @param abortable abortable in case of fatal error
   */
  public MasterAddressTracker(ZooKeeperWatcher watcher, Abortable abortable) {
    super(watcher, watcher.getMasterAddressZNode(), abortable);
  }

  /**
   * Get the address of the current master if one is available.  Returns null
   * if no current master.
   * @return Server name or null if timed out.
   */
  public ServerName getMasterAddress() {
    return getMasterAddress(false);
  }

  /**
   * Get the address of the current master if one is available.  Returns null
   * if no current master. If refresh is set, try to load the data from ZK again,
   * otherwise, cached data will be used.
   *
   * @param refresh whether to refresh the data by calling ZK directly.
   * @return Server name or null if timed out.
   */
  public ServerName getMasterAddress(final boolean refresh) {
    return ZKUtil.znodeContentToServerName(super.getData(refresh));
  }

  /**
   * Get master address.
   * Use this instead of {@link #getMasterAddress()} if you do not have an
   * instance of this tracker in your context.
   * @param zkw ZooKeeperWatcher to use
   * @return ServerName stored in the the master address znode or null if no
   * znode present.
   * @throws KeeperException 
   * @throws IOException 
   */
  public static ServerName getMasterAddress(final ZooKeeperWatcher zkw)
  throws KeeperException, IOException {
    byte [] data = ZKUtil.getData(zkw, zkw.getMasterAddressZNode());
    if (data == null){
      throw new IOException("Can't get master address from ZooKeeper; znode data == null");
    }
    return ZKUtil.znodeContentToServerName(data);
  }

  /**
   * Set master address into the <code>master</code> znode or into the backup
   * subdirectory of backup masters; switch off the passed in <code>znode</code>
   * path.
   * @param zkw The ZooKeeperWatcher to use.
   * @param znode Where to create the znode; could be at the top level or it
   * could be under backup masters
   * @param master ServerName of the current master
   * @return true if node created, false if not; a watch is set in both cases
   * @throws KeeperException
   */
  public static boolean setMasterAddress(final ZooKeeperWatcher zkw,
      final String znode, final ServerName master)
  throws KeeperException {
    return ZKUtil.createEphemeralNodeAndWatch(zkw, znode, getZNodeData(master));
  }

  /**
   * Check if there is a master available.
   * @return true if there is a master set, false if not.
   */
  public boolean hasMaster() {
    return super.getData(false) != null;
  }

  /**
   * @param sn
   * @return Content of the master znode as a serialized pb with the pb
   * magic as prefix.
   */
   static byte [] getZNodeData(final ServerName sn) {
     ZooKeeperProtos.Master.Builder mbuilder = ZooKeeperProtos.Master.newBuilder();
     HBaseProtos.ServerName.Builder snbuilder = HBaseProtos.ServerName.newBuilder();
     snbuilder.setHostName(sn.getHostname());
     snbuilder.setPort(sn.getPort());
     snbuilder.setStartCode(sn.getStartcode());
     mbuilder.setMaster(snbuilder.build());
     return ProtobufUtil.prependPBMagic(mbuilder.build().toByteArray());
   }
}