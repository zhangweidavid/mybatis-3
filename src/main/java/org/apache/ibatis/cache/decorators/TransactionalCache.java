/**
 *    Copyright 2009-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * 二级事务缓存
 * 这个类在Session内持有所有添加到二级缓存的对象
 * 当提交被调用的时候将缓存对象放入到缓存中，或Session回滚到时候丢弃掉
 * 
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  private final Cache delegate;
  private boolean clearOnCommit;
  //待提交缓存
  private final Map<Object, Object> entriesToAddOnCommit;

  //缓存中丢失到数据集合
  private final Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<Object, Object>();
    this.entriesMissedInCache = new HashSet<Object>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public Object getObject(Object key) {
    //获取缓存对象
    Object object = delegate.getObject(key);
    //如果缓存对象为null则将key加入到entriesMissedInCache
    if (object == null) {
      entriesMissedInCache.add(key);
    }
    //如果缓存对象不为null但是如果clearOnCommit为true，标示缓存处于清空中了，则返回null
    if (clearOnCommit) {
      return null;
    } else {
      return object;
    }
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  //添加缓存，不是真实添加到缓存中，而是添加到一个缓冲池中，等待提交
  @Override
  public void putObject(Object key, Object object) {
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  @Override
  public void clear() {
    //设置提交时清空
    clearOnCommit = true;
    //清空缓冲池
    entriesToAddOnCommit.clear();
  }

  //提交缓存，如果配置了提交时清空，则清空缓存
  public void commit() {
    //缓存已经触发过clear方法了
    if (clearOnCommit) {
      delegate.clear();
    }
    //刷新缓冲池
    flushPendingEntries();
    //重置缓存
    reset();
  }

  public void rollback() {

    unlockMissedEntries();
    //重置缓存
    reset();
  }
   //重置事务缓存
  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  private void flushPendingEntries() {
    //遍历缓冲，将缓冲池中的数据保存到缓存中
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    //遍历缓存中缺失的对象
    for (Object entry : entriesMissedInCache) {
      //如果缓存池中不存在则将其添加到缓存中，缓存值为null
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }
    //删除丢失到缓存
  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifiying a rollback to the cache adapter."
            + "Consider upgrading your cache adapter to the latest version.  Cause: " + e);
      }
    }
  }

}
