package com.ustadmobile.core.view;

import com.ustadmobile.core.impl.UmCallback;

/**
 * Created by mike on 1/6/18.
 */

public interface ScormPackageView extends UstadView{

    String VIEW_NAME = "ScormPackage";

    void setTitle(String title);

    void loadUrl(String url);

    void mountZip(String zipUri, UmCallback callback);

}
