package com.ustadmobile.port.sharedse.networkmanager;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.ustadmobile.port.sharedse.networkmanager.BleMessageUtil.bleMessageLongToBytes;
import static org.junit.Assert.assertTrue;

/**
 * Test class which test {{@link BleMessageUtil}} to make sure it behaves as expected when
 * converting List of entries to bytes and vice-versa
 *
 * @author kileha3
 */
public class BleMessageUtilTest {

    private List<Long> entryList;

    private List<Boolean> entryStatusResponse;

    @Before
    public void setUp(){
        this.entryList = Arrays.asList(1056289670L,4590875612L,9076137860L,2912543894L,8965341254L);
        this.entryStatusResponse = Arrays.asList(true,false,true,true,false);
    }

    @Test
    public void givenListOfEntriesInLong_whenConverted_shouldReturnBytes(){

        byte[] entriesInBytes = bleMessageLongToBytes(entryList);

        assertTrue("Entry list was converted to bytes ",entriesInBytes.getClass().isArray());
    }

    @Test
    public void givenListOfEntriesInLong_whenConvertedToBytesAndConvertedBackToList_thenShouldTheSame(){

        byte[] entriesInBytes = bleMessageLongToBytes(entryList);
        List<Long> entriesInListOfLong =  BleMessageUtil.bleMessageBytesToLong(entriesInBytes);

        assertTrue("Entry lists before and after conversions are equal",
                entryList.equals(entriesInListOfLong));
    }

}