package com.jessamine.pdfreview.confluence;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParsePosition;

import com.atlassian.spring.container.ContainerManager;

import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.links.LinkRenderer;
import com.atlassian.renderer.links.Link;
import com.atlassian.confluence.core.ContentPropertyManager;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.renderer.PageContext;
import com.atlassian.confluence.pages.AbstractPage;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.user.User;

import com.opensymphony.webwork.ServletActionContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Cookie;


public class ReviewStatus extends BaseMacro
{
   public static final SimpleDateFormat tagDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");

   public static final int Submitted = 1;
   public static final int UnderReview = 2;
   public static final int Abandoned = 3;
   public static final int Completed = 4;
   public static final int ReOpened = 5;

   public static String codeToString(int statusCode)
   {
      switch (statusCode)
      {
         case Submitted: return "Submitted";
         case UnderReview: return "Under review";
         case Abandoned: return "Abandoned";
         case Completed: return "Completed";
         case ReOpened: return "Re-opened";
         default: return "Unknown";
      }
   }

   public static void updateStatus(ContentPropertyManager cpm, Page reviewIndexPage, User user, String reviewId, int newStatus)
      { updateStatus(cpm, reviewIndexPage, user, reviewId, newStatus, null, false); }

   public static void updateStatus(ContentPropertyManager cpm, Page reviewIndexPage, User user, String reviewId, int newStatus, Date date)
      { updateStatus(cpm, reviewIndexPage, user, reviewId, newStatus, date, false); }

   public static void updateStatus(ContentPropertyManager cpm, Page reviewIndexPage, User user, String reviewId, int newStatus, Date date, boolean create)
   {
      if (user == null)
         return;

      if (getStatus(cpm, reviewIndexPage, reviewId) == newStatus)
         return;

      String current = cpm.getTextProperty(reviewIndexPage, "pdfreview."+reviewId+".state");
      if (current == null && create == false)
         return;

      if (date == null)
         date = new Date();

      String updated = Integer.toString(newStatus) + ":" + tagDateFormat.format(date) + ":" + user.getName();
      if (current != null)
         updated += "," + current;
      cpm.setTextProperty(reviewIndexPage, "pdfreview."+reviewId+".state", updated);
   }
   public static int getStatus(ContentPropertyManager cpm, Page reviewIndexPage, String reviewId)
   {
      try
      {
         String current = cpm.getTextProperty(reviewIndexPage, "pdfreview."+reviewId+".state");
         return Integer.parseInt(current.substring(0, current.indexOf(':')));
      }
      catch (Exception e)
      {
         return -1;
      }
   }

   private SettingsManager settingsManager;
   private PageManager pageManager;
   private AttachmentManager attachmentManager;
   private SpaceManager spaceManager;
   private ContentPropertyManager contentPropertyManager;

   public static String getFriendlyTime(Date dateTime)
   {
       StringBuffer sb = new StringBuffer();
       Date current = new Date();
       long diffInSeconds = (current.getTime() - dateTime.getTime()) / 1000;

       long sec = (diffInSeconds >= 60 ? diffInSeconds % 60 : diffInSeconds);
       long min = (diffInSeconds = (diffInSeconds / 60)) >= 60 ? diffInSeconds % 60 : diffInSeconds;
       long hrs = (diffInSeconds = (diffInSeconds / 60)) >= 24 ? diffInSeconds % 24 : diffInSeconds;
       long days = (diffInSeconds = (diffInSeconds / 24)) >= 30 ? diffInSeconds % 30 : diffInSeconds;
       long months = (diffInSeconds = (diffInSeconds / 30)) >= 12 ? diffInSeconds % 12 : diffInSeconds;
       long years = (diffInSeconds = (diffInSeconds / 12));

       if (years > 0) {
           if (years == 1) {
               sb.append("a year");
           } else {
               sb.append(years + " years");
           }
           if (years <= 6 && months > 0) {
               if (months == 1) {
                   sb.append(" and a month");
               } else {
                   sb.append(" and " + months + " months");
               }
           }
       } else if (months > 0) {
           if (months == 1) {
               sb.append("a month");
           } else {
               sb.append(months + " months");
           }
           if (months <= 6 && days > 0) {
               if (days == 1) {
                   sb.append(" and a day");
               } else {
                   sb.append(" and " + days + " days");
               }
           }
       } else if (days > 0) {
           if (days == 1) {
               sb.append("a day");
           } else {
               sb.append(days + " days");
           }
           if (days <= 3 && hrs > 0) {
               if (hrs == 1) {
                   sb.append(" and an hour");
               } else {
                   sb.append(" and " + hrs + " hours");
               }
           }
       } else if (hrs > 0) {
           if (hrs == 1) {
               sb.append("an hour");
           } else {
               sb.append(hrs + " hours");
           }
           if (min > 1) {
               sb.append(" and " + min + " minutes");
           }
       } else if (min > 0) {
           if (min == 1) {
               sb.append("a minute");
           } else {
               sb.append(min + " minutes");
           }
           if (sec > 1) {
               sb.append(" and " + sec + " seconds");
           }
       } else {
           if (sec <= 1) {
               sb.append("about a second");
           } else {
               sb.append("about " + sec + " seconds");
           }
       }

       sb.append(" ago");

       return sb.toString();
   }

   public boolean isInline()
   {
      return false;
   }

   public boolean hasBody()
   {
      return false;
   }

   public RenderMode getBodyRenderMode()
   {
      return RenderMode.NO_RENDER;
   }

   ReviewStatus(SettingsManager settingsManager, PageManager pageManager, AttachmentManager attachmentManager, SpaceManager spaceManager, ContentPropertyManager contentPropertyManager)
   {
      this.settingsManager = settingsManager;
      this.pageManager = pageManager;
      this.attachmentManager = attachmentManager;
      this.spaceManager = spaceManager;
      this.contentPropertyManager = contentPropertyManager;
   }


   public String execute(Map params, String body, RenderContext renderContext)
      throws MacroException
   {
      PageContext pageContext = (PageContext) renderContext;
      Page page = (Page) pageContext.getEntity();

      StringBuilder rc = new StringBuilder();

      String lastReviewedOn = contentPropertyManager.getStringProperty(page, "pdfreview.lastReviewedOn");
      
      String reviewHistory;
      try // trap transitional prop type
      {
         reviewHistory = contentPropertyManager.getTextProperty(page, "pdfreview.reviewHistory");
      }
      catch (Exception e)
      {
         return "<a href=\"/plugins/pdfreview/manage-review-logs.action?page="+page.getId()+"\"><strong>Fix this legacy tag</strong></a>";
      }

      if (lastReviewedOn == null || reviewHistory == null)
         return "";

      rc.append("<div id='review-status'>");

      class ReviewLog
      {
         ReviewLog(String pageid, Page page, String reviewid)
         {
            this.pageid = pageid;
            this.page = page;
            this.reviewid = reviewid;
         }
         public String pageid;
         public Page page;
         public String reviewid;
      };

      List<ReviewLog> history = new ArrayList<ReviewLog>();

      for (String s : reviewHistory.split(","))
      {
         String[] spec = s.split(":");
         Page reviewPage = pageManager.getPage(Integer.parseInt(spec[0]));
         if (reviewPage != null)
            history.add(new ReviewLog(spec[0], reviewPage, spec[1]));
      }

      if (history.isEmpty())
         return "";

      rc.append("<p><strong>Last reviewed</strong> " + getFriendlyTime(tagDateFormat.parse(lastReviewedOn, new ParsePosition(0))) + "</p>");
      rc.append("<p><a href=\"/plugins/pdfreview/manage-review-logs.action?page="+page.getId()+"\"><strong>Review history:</strong></a><ol>");
      int n = history.size() + 1;
      for (ReviewLog log : history)
      {
         --n;
         AbstractPage reviewPage = log.page;
         if (reviewPage == null)
         {
            rc.append("<li value='"+n+"'>" + log.pageid + ": Failed to get review page</li>");
            continue;
         }
         String stamp = reviewPage.getLastModificationDate().toString();
         reviewPage = reviewPage.getLatestVersion();
         rc.append("<li value='"+n+"'><a href=\"" + reviewPage.getUrlPath() + "#" + (reviewPage.getTitle().replace(" ", "")+"-"+log.reviewid) + "\">" + stamp + "</a></li>");
      }
      rc.append("</ol></p>");
      rc.append("</div>");

      return rc.toString();
   }
}

