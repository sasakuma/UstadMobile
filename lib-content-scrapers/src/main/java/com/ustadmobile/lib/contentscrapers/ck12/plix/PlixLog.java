package com.ustadmobile.lib.contentscrapers.ck12.plix;

public class PlixLog {

    public Message message;

    public class Message{

        public String method;

        public Params params;

        public class Params{

            public Response response;

            public class Response{

                public String mimeType;

                public String url;


            }

        }

    }

}
