package com.ustadmobile.core.db.dao;

import com.ustadmobile.core.impl.UmCallback;
import com.ustadmobile.lib.database.annotation.UmDao;
import com.ustadmobile.lib.database.annotation.UmInsert;
import com.ustadmobile.lib.database.annotation.UmQuery;
import com.ustadmobile.lib.db.entities.Person;

@UmDao
public abstract class PersonDao implements BaseDao<Person>{

    @Override
    @UmInsert
    public abstract long insert(Person entity);

    @UmInsert
    @Override
    public abstract void insertAsync(Person entity, UmCallback<Long> result);

    @Override
    @UmQuery("SELECT * From Person WHERE personUid = :uid")
    public abstract Person findByUid(long uid);
}