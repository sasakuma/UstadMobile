package com.ustadmobile.port.android.db.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;

import com.ustadmobile.core.db.dao.DownloadJobItemHistoryDao;
import com.ustadmobile.lib.db.entities.DownloadJobItemHistory;

import java.util.List;

/**
 * Created by mike on 2/2/18.
 */

@Dao
public abstract class DownloadJobItemHistoryDaoAndroid extends DownloadJobItemHistoryDao {

    @Override
    @Query("SELECT * FROM DownloadJobItemHistory WHERE networkNode = :nodeId AND startTime >= :since")
    public abstract List<DownloadJobItemHistory> findHistoryItemsByNetworkNodeSince(int nodeId, long since);
}