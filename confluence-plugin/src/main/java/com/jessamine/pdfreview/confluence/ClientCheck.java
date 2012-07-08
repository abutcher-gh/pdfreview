package com.jessamine.pdfreview.confluence;

import com.atlassian.confluence.core.ConfluenceActionSupport;

public class ClientCheck extends ConfluenceActionSupport
{
   private static final long serialVersionUID = -6840477097021818330L;
   public static final String clientUrl = "https://www.assembla.com/spaces/ajbhome-process/wiki#pdf_review_client_scripts";
   public static final double requiredClientVersion = 0.2;

   ClientCheck()
   {
   }

   private double clientVersion;
   public void setClientVersion(double v) { clientVersion = v; }

   public double getClientVersion() { return clientVersion; }
   public String getClientUrl() { return clientUrl; }
   public double getRequiredClientVersion() { return requiredClientVersion; }

   public String execute()
   {
      if (clientVersion >= requiredClientVersion)
         return "success";
      return "error";
   }
}

