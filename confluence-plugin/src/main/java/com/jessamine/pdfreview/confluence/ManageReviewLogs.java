package com.jessamine.pdfreview.confluence;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

import java.io.File;
import java.io.FileInputStream;

import java.io.StringWriter;
import java.io.PrintWriter;

import java.util.Date;
import java.text.SimpleDateFormat;

import com.atlassian.confluence.setup.BootstrapManager;
import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.confluence.core.DefaultSaveContext;
import com.atlassian.confluence.core.ContentPropertyManager;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.security.SpacePermission;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.user.User;

// import org.springframework.orm.hibernate.HibernateSystemException;
// import net.sf.hibernate.PropertyValueException;


public class ManageReviewLogs extends ConfluenceActionSupport
{
   private static final long serialVersionUID = 9063317947114043994L;
   private BootstrapManager bootstrapManager;
   private PageManager pageManager;
   private AttachmentManager attachmentManager;
   private SpaceManager spaceManager;
   private ContentPropertyManager contentPropertyManager;

   private String spaceKey;
   private String reviewSpaceKey;
   private String reviewPath;
   private String reviewLabel;
   class PageTree
   {
      PageTree() { this.root = true; id = 0; }
      PageTree(long id) { this.id = id; }
      public final long id;
      public boolean reviewed = false;
      public boolean childReviewed = false;
      public boolean root = false;
      public List<PageTree> subtrees = new LinkedList<PageTree>();
   };
   private String reviewPages;
   private PageTree reviewPageTree = new PageTree();
   private String pdfFile;

   public void setKey(String s)
   {
      spaceKey = s;
      reviewSpaceKey = s + "REV";
   }
   
   private String page;
   public String getPage() { return page; }
   private boolean forceClear = false;
   private Page thePage;

   public void setPage(String s) { page = s; try { thePage = pageManager.getPage(Long.parseLong(s)); } catch (Exception e) {} }
   public void setForceClear(boolean b) { forceClear = b; }

   public void setReviewPath(String s) { reviewPath = s; }
   public void setReviewLabel(String s) { reviewLabel = s; }
   public void setReviewPages(String s)
   {
      this.reviewPages = s;

      try {

      String[] pages = s.split(",");
      List<Long> pageIds = new LinkedList<Long>();
      for (String p : pages)
         pageIds.add(new Long(p));

      while (!pageIds.isEmpty())
         reviewPageTree.subtrees.add(createTreeByRelevance(pageIds, pageIds.get(0)));

      } catch (Exception e)
      {
         log(e.toString());
         StringWriter stack = new StringWriter();
         e.printStackTrace(new PrintWriter(stack));
         error = e.toString() + "\n" + stack.toString();
      }
   }
   PageTree createTreeByRelevance(List<Long> pageIds, Long pageId)
   {
      log("createTreeByRelevance("+pageId+")");

      PageTree rc = null;

      if (pageIds.contains(new Long(pageId)))
      {
         rc = new PageTree(pageId.longValue());
         rc.reviewed = true;
         pageIds.remove(pageId);
      }

      if (pageIds.isEmpty())
         return rc;

      Page page = pageManager.getPage(pageId);

      if (page == null || !page.hasChildren())
         return rc;

      List<Page> children = page.getChildren();

      List<PageTree> subtrees = new LinkedList<PageTree>();

      boolean directChildReviewed = false;

      for (Page p : children)
      {
         PageTree t = createTreeByRelevance(pageIds, new Long(p.getId()));

         if (t != null && (t.reviewed || t.childReviewed))
         {
            if (rc == null)
               rc = new PageTree(pageId.longValue());
            rc.childReviewed = true;
            if (t.reviewed)
               directChildReviewed = true;
            subtrees.add(t);
         }
         else
         {
            subtrees.add(new PageTree(p.getId()));
         }
      }
      if (rc != null && rc.childReviewed)
      {
         if (directChildReviewed)
            rc.subtrees = subtrees;
         else
            for (PageTree t : subtrees)
               if (t.childReviewed)
                  rc.subtrees.add(t);
      }
      return rc;
   }
   String wikiFormatTree(PageTree t, String level)
   {
      log("wikiFormatTree("+t.id+", '"+level+"')");
      String rc = "";
      if (!t.root)
      {
         Page page = pageManager.getPage(t.id);
         if (t.reviewed)
            rc = level + " [" + page.getTitle() + " ^(v" + page.getVersion() + ")^"+ "|$" + t.id + "]\n";
         else
            rc = level + " {color:gray}" + page.getTitle() + " ^(omitted)^{color}\n";
      }
      if (!t.subtrees.isEmpty())
      {
         level += "-";
         for (PageTree st : t.subtrees)
            rc += wikiFormatTree(st, level);
      }
      return rc;
   }
   final static SimpleDateFormat tagDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
   void markReviewedPages(PageTree t, String reviewPage, String reviewId, Date date)
   {
      log("markReviewedPages("+t.id+", '"+reviewPage+"', '"+reviewId+"')");
      if (!t.root)
      {
         Page page = pageManager.getPage(t.id);
         if (t.reviewed)
         {
            contentPropertyManager.setStringProperty(page, "pdfreview.lastReviewedOn", tagDateFormat.format(date));
            
            String reviewHistory = contentPropertyManager.getTextProperty(page, "pdfreview.reviewHistory");
            if (reviewHistory == null || reviewHistory.isEmpty())
               reviewHistory = "";
            else
               reviewHistory = "," + reviewHistory;

            contentPropertyManager.setTextProperty(page, "pdfreview.reviewHistory", reviewPage + ":" + reviewId + reviewHistory);
         }
      }
      if (!t.subtrees.isEmpty())
      {
         for (PageTree st : t.subtrees)
            markReviewedPages(st, reviewPage, reviewId, date);
      }
   }
   public void setFile(String s) { pdfFile = s; }

   private String upgrade;
   public void setUpgrade(String upgrade) { this.upgrade = upgrade; }

   ManageReviewLogs(BootstrapManager bootstrapManager, PageManager pageManager, AttachmentManager attachmentManager, SpaceManager spaceManager, ContentPropertyManager contentPropertyManager)
   {
      this.bootstrapManager = bootstrapManager;
      this.pageManager = pageManager;
      this.attachmentManager = attachmentManager;
      this.spaceManager = spaceManager;
      this.contentPropertyManager = contentPropertyManager;
   }

   String logString;
   void log(String s)
   {
      logString += "<li>" + s + "</li>";
   }

   public String xyz()
   {
      User user = AuthenticatedUserThreadLocal.getUser();
      String greeting = "Flurb";
      if (user!=null) {
         greeting = "Hello " + user.getFullName() + "<br><br>";
      }
      return "<ul>"
         + "<li>Log:<ul>" + logString + "</ul></li>"
         + "<li>"+bootstrapManager.getConfluenceHome()+"</li>"
         + "<li>"+spaceKey+"</li>"
         + "<li>"+reviewPath+"</li>"
         + "<li>"+reviewLabel+"</li>"
         + "<li>"+wikiFormatTree(reviewPageTree,"")+"</li>"
         + "<li>"+pdfFile+"</li>"
         + "<li>"+done+"</li>"
         + "<li>"+greeting+"</li>"
         + "</ul>"
         ;
   }

   private boolean done = false;

   private String error;
   private String redirect;

   public String getError()
   {
      return error;
   }

   public String getRedirect()
   {
      return redirect;
   }

   public String execute()
   {
      if (upgrade != null)
      {
         for (Page p : (List<Page>) pageManager.getPages(spaceManager.getSpace(upgrade), false))
         {
            try
            {
               String s = contentPropertyManager.getStringProperty(p, "pdfreview.reviewHistory");
               if (s != null)
               {
                  contentPropertyManager.removeProperty(p, "pdfreview.reviewHistory");
                  contentPropertyManager.setTextProperty(p, "pdfreview.reviewHistory", s);
               }
            }
            catch (Exception e)
            {
            }
         }
         error = "Done upgrade";
         return "input";
      }

      if (thePage == null || forceClear == false)
         return "input";

      if (forceClear)
      {
         User user = AuthenticatedUserThreadLocal.getUser();
         if (user != null && (user.getName().equals("abutcher") || user.getName().equals("ajb")))
         {
            contentPropertyManager.removeProperty(thePage, "pdfreview.lastReviewedOn");
            contentPropertyManager.removeProperty(thePage, "pdfreview.reviewHistory");
            redirect = thePage.getUrlPath();
            return "success";
         }
      }

      if (error == null)
         return "input";

      return "error";
/*
      // FIXME: ugly catch all
      try {

      User user = AuthenticatedUserThreadLocal.getUser();
      if (user == null)
      {
         error = "Not authenticated.  Cannot proceed.";
         return "error";
      }
      Space space = spaceManager.getSpace(spaceKey);
      if (space == null)
      {
         error = "No such space '" + spaceKey + "'";
         return "error";
      }
      Space reviewSpace = spaceManager.getSpace(reviewSpaceKey);
      if (reviewSpace == null)
      {
         reviewSpace = spaceManager.createSpace(
               reviewSpaceKey,
               space.getName() + " Reviews",
               "Content reviewing for the '" + space.getName() + "' space.", user);
         if (reviewSpace == null)
         {
            error = "Cannot create review space '" + reviewSpaceKey + "'.  Get your administrator to do so.";
            return "error";
         }

         List<SpacePermission> perms = new ArrayList<SpacePermission>();

         perms.add(SpacePermission.createAnonymousSpacePermission(SpacePermission.VIEWSPACE_PERMISSION, reviewSpace));
         perms.add(SpacePermission.createGroupSpacePermission(SpacePermission.CREATEEDIT_PAGE_PERMISSION, reviewSpace, "confluence-users"));
         perms.add(SpacePermission.createGroupSpacePermission(SpacePermission.CREATE_ATTACHMENT_PERMISSION, reviewSpace, "confluence-users"));
         perms.add(SpacePermission.createGroupSpacePermission(SpacePermission.COMMENT_PERMISSION, reviewSpace, "confluence-users"));

         reviewSpace.setPermissions(perms);
      }

      String[] tree = reviewPath.substring(1).split("/");

      Page reviewPage = null;

      DefaultSaveContext ctx = new DefaultSaveContext();
      ctx.setUpdateLastModifier(false);
      ctx.setMinorEdit(true);

      String pageLinks; // create wiki tree to link back to real pages

      for (String s : tree)
      {
         Page p = pageManager.getPage(reviewSpaceKey, s);

         log("Page "+p+" ["+s+"]");

         if (p == null)
         {
            p = new Page();
            p.setSpace(reviewSpace);
            p.setTitle(s);
            p.setContent("");
            try
            {
               pageManager.saveContentEntity(p, ctx);
            }
            catch (Exception ex)
            {
               log("Tree creation "+reviewPath+" failed at page '"+s+"'. "+ex.toString());
            }
            // catch (HibernateSystemException ex) {
            //    PropertyValueException pve = (PropertyValueException) ex.getCause();
            //    System.err.println("Class: " + pve.getPersistentClass().getName());
            //    System.err.println("Property: " + pve.getPropertyName());            
            // }
            if (reviewPage != null)
            {
               reviewPage.addChild(p);
               pageManager.saveContentEntity(reviewPage, ctx);
            }
         }
         reviewPage = p;
      }

      if (reviewPage == null)
      {
         error = "Could not form review page.";
         return "error";
      }

      ctx.setMinorEdit(false);
      ctx.setUpdateLastModifier(true);

      String existingContent = reviewPage.getContent();

      log("Getting prop from  " + reviewPage.getTitle());

      String latestReview = contentPropertyManager.getStringProperty(reviewPage, "pdfreview.latest");

      log("latestReview = " +latestReview);

      Page oldPage = null;
      if (latestReview != null)
        oldPage = (Page) reviewPage.clone();

      try {
         latestReview = Integer.toString(Integer.parseInt(latestReview) + 1);
      } catch (Exception e) {
         latestReview = "1";
      }

      log("latestReview now = " +latestReview);

      contentPropertyManager.setStringProperty(reviewPage, "pdfreview.latest", latestReview);
      contentPropertyManager.setStringProperty(reviewPage, "pdfreview." + latestReview, reviewPages);

      log("SetProp " +latestReview);

      Date date = new Date();
      SimpleDateFormat headingDateFormat = new SimpleDateFormat("d MMMM yyyy (yyyyMMdd-hhmmss)");

      String reviewTag = reviewPage.getTitle().replace(" ","-") + "-" + latestReview + "-" + tagDateFormat.format(date);
      log("reviewTag = " +reviewTag);
      String reviewHeading = "Review " + latestReview + " - " + headingDateFormat.format(date);

      log("reviewHeading = " +reviewHeading);

      markReviewedPages(reviewPageTree, Long.toString(reviewPage.getId()), latestReview, date);

      reviewPage.setContent(
             "h1. " + reviewHeading + "{anchor:"+latestReview+"}\n"
           + "{section:border=true}\n"
           + "{column:width=50%}\n"
           +    "h3. This review includes the following pages\n"
           +    wikiFormatTree(reviewPageTree, "") + "\n"
           +    "h3. Review progress\n"
           +    "{review-progress:tag="+reviewTag+"|id="+latestReview+"}\n"
           + "{column}\n"
           + "{column:width=50%}\n"
           +    "{viewpdf:"+reviewTag+".pdf}\n"
           + "{column}\n"
           + "{section}\n"
           + "----\n\n"
           + existingContent
           );

      log("content updated");

      File file = new File(pdfFile.replace("/download", bootstrapManager.getConfluenceHome()));

      Attachment attachment = new Attachment(reviewTag+".pdf", "application/pdf", file.length(), "Snapshot for review " + latestReview + " of " + reviewPage.getTitle());

      log(attachment.toString());

      FileInputStream in = new FileInputStream(file);

      log(in.toString());

      reviewPage.addAttachment(attachment);

      log("attached");

      attachmentManager.saveAttachment(attachment, null, in);

      log("updated data");

      in.close();
      in = null;
      file = null;

      try
      {
         if (oldPage == null)
            pageManager.saveContentEntity(reviewPage, ctx);
         else
            pageManager.saveContentEntity(reviewPage, oldPage, ctx);
      }
      catch (Exception ex)
      {
         log("Failed to update review page '"+reviewPage.getTitle()+"'.  "+ex.toString());
         return "error";
      }
      // catch (HibernateSystemException ex)
      // {
      //    PropertyValueException pve = (PropertyValueException) ex.getCause();
      //    System.err.println("X Class: " + pve.getPersistentClass().getName());
      //    System.err.println("X Property: " + pve.getPropertyName());            
      // }

      redirect = "/display/"+reviewSpaceKey+"/"+reviewPage.getTitle();

      done = true;

      if (error == null || error.isEmpty())
         return "success";
      else
         return "error";

      } catch (Exception e)
      {
         StringWriter stack = new StringWriter();
         e.printStackTrace(new PrintWriter(stack));
         error = e.toString() + "\n" + stack.toString();
         return "error";
      }
      */
   }
}

