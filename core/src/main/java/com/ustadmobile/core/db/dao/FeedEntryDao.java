package com.ustadmobile.core.db.dao;

import com.ustadmobile.core.db.UmProvider;
import com.ustadmobile.core.impl.UmCallback;
import com.ustadmobile.lib.database.annotation.UmDao;
import com.ustadmobile.lib.database.annotation.UmInsert;
import com.ustadmobile.lib.database.annotation.UmQuery;
import com.ustadmobile.lib.db.entities.FeedEntry;

import java.util.List;

@UmDao
public abstract class FeedEntryDao implements BaseDao<FeedEntry> {

    @UmInsert
    public abstract long insert(FeedEntry entity);

    @UmInsert
    public abstract void insertAsync(FeedEntry entity, UmCallback<Long> result);

    @UmQuery("SELECT * FROM FeedEntry WHERE feedEntryUid = :uid")
    public abstract FeedEntry findByUid(long uid) ;

    @UmQuery("SELECT * FROM FeedEntry WHERE feedEntryPersonUid = :personUid")
    public abstract UmProvider<FeedEntry> findByPersonUid(long personUid);

    @UmQuery("SELECT * FROM FeedEntry WHERE feedEntryPersonUid = :personUid")
    public abstract List<FeedEntry> findByPersonUidList(long personUid);

    @UmQuery("SELECT * FROM FeedEntry")
    public abstract List<FeedEntry> findAll();

}