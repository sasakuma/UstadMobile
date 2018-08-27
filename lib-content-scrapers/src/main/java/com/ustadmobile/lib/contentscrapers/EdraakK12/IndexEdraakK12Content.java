package com.ustadmobile.lib.contentscrapers.EdraakK12;

import com.google.gson.JsonSyntaxException;
import com.ustadmobile.lib.contentscrapers.ContentScraperUtil;
import com.ustadmobile.lib.db.entities.OpdsEntry;
import com.ustadmobile.lib.db.entities.OpdsEntryParentToChildJoin;
import com.ustadmobile.lib.db.entities.OpdsEntryWithRelations;
import com.ustadmobile.lib.db.entities.OpdsLink;
import com.ustadmobile.lib.util.UmUuidUtil;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class IndexEdraakK12Content {

    private List<OpdsEntryWithRelations> entryWithRelationsList;
    private List<OpdsEntryParentToChildJoin> parentToChildJoins;
    private URL url;
    private File destinationDirectory;
    private ContentResponse response;


    public static void main(String[] args) {
        if(args.length != 2) {
            System.err.println("Usage: <edraak k12 json url> <file destination>");
            System.exit(1);
        }

        System.out.println(args[0]);
        System.out.println(args[1]);
        try {
            new IndexEdraakK12Content().findContent(args[0], new File(args[1]));
        }catch(IOException e) {
            System.err.println("Exception running scrapContent");
            e.printStackTrace();
        }

    }

    public void findContent(String urlString, File destinationDir) throws IOException {

        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            System.out.println("Index Malformed url" + urlString);
            throw new IllegalArgumentException("Malformed url" + urlString, e);
        }

        destinationDir.mkdirs();
        destinationDirectory = destinationDir;

        try {
            response = ContentScraperUtil.parseJson(url, ContentResponse.class);
        } catch (IOException | JsonSyntaxException e) {
            throw new IllegalArgumentException("JSON INVALID", e.getCause());
        }

        entryWithRelationsList = new ArrayList<>();
        parentToChildJoins = new ArrayList<>();

        OpdsEntryWithRelations edraakEntry = new OpdsEntryWithRelations(UmUuidUtil.encodeUuidWithAscii85(UUID.randomUUID()),
               "https://www.edraak.org/k12/", "Edraak K12");


        OpdsEntryWithRelations parentEntry = new OpdsEntryWithRelations(UmUuidUtil.encodeUuidWithAscii85(UUID.randomUUID()),
               urlString.substring(0, urlString.indexOf("component/")) + response.id, response.title);

        OpdsEntryParentToChildJoin join = new OpdsEntryParentToChildJoin(edraakEntry.getUuid(),
                parentEntry.getUuid(), 0);


        entryWithRelationsList.add(edraakEntry);
        entryWithRelationsList.add(parentEntry);
        parentToChildJoins.add(join);

        findImportedComponent(response, parentEntry);

    }

    private void findImportedComponent(ContentResponse parent, OpdsEntryWithRelations parentEntry)  {

        if(ContentScraperUtil.isImportedComponent(parent.component_type)){

            // found the last child
            EdraakK12ContentScraper scraper = new EdraakK12ContentScraper(
                    EdraakK12ContentScraper.generateUrl(url.getProtocol() + "://" + url.getHost() + (url.getPort() > 0 ? (":" + url.getPort()) : "") + "/api/", parent.id, parent.program == 0 ? response.program : parent.program),
                    destinationDirectory);
            try{
                scraper.scrapContent();
            }catch (Exception e){
                System.err.println(e.getCause());
                return;
            }

            OpdsLink newEntryLink = new OpdsLink(parentEntry.getUuid(), "application/zip",
                    destinationDirectory.getParent() + "/" + parent.id + ".zip", OpdsEntry.LINK_REL_ACQUIRE);
            newEntryLink.setLength(new File(destinationDirectory.getParentFile(), response.id + ".zip").length());
            parentEntry.setLinks(Collections.singletonList(newEntryLink));

        }else{

            for(ContentResponse children: parent.children){

                OpdsEntryWithRelations newEntry = new OpdsEntryWithRelations(
                        UmUuidUtil.encodeUuidWithAscii85(UUID.randomUUID()),
                        url.toString().substring(0, url.toString().indexOf("component/")) + children.id,
                        children.title);

                OpdsEntryParentToChildJoin join = new OpdsEntryParentToChildJoin(parentEntry.getUuid(),
                        newEntry.getUuid(), children.child_index);

                entryWithRelationsList.add(newEntry);
                parentToChildJoins.add(join);

                findImportedComponent(children, parentEntry);

            }

        }
    }


    public void findContent(String contentId, String baseUrl, int programId, File destinationDir) throws IOException {
        findContent(baseUrl + "component/" +  contentId + "/?states_program_id=" + programId, destinationDir);
    }




}