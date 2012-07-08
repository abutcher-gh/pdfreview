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
import com.atlassian.xwork.FileUploadUtils;
import com.atlassian.xwork.FileUploadUtils.FileUploadException;
import com.atlassian.confluence.themes.ThemeManager;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext;


public class StartReview extends ConfluenceActionSupport
{
   private static final long serialVersionUID = 3738537989616375864L;
   private BootstrapManager bootstrapManager;
   private PageManager pageManager;
   private AttachmentManager attachmentManager;
   private SpaceManager spaceManager;
   private ContentPropertyManager contentPropertyManager;
   private ThemeManager themeManager;
   private BandanaManager bandanaManager;

   StartReview(BootstrapManager bootstrapManager, PageManager pageManager, AttachmentManager attachmentManager, SpaceManager spaceManager, ContentPropertyManager contentPropertyManager, ThemeManager themeManager, BandanaManager bandanaManager)
   {
      this.bootstrapManager = bootstrapManager;
      this.pageManager = pageManager;
      this.attachmentManager = attachmentManager;
      this.spaceManager = spaceManager;
      this.contentPropertyManager = contentPropertyManager;
      this.themeManager = themeManager;
      this.bandanaManager = bandanaManager;
   }

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
   private File pdfContent; // prob should be array of bytes

   public void setKey(String s)
   {
      spaceKey = s;
      if (reviewSpaceKey == null)
    	  reviewSpaceKey = s + "REV";
   }
   public void setReviewSpaceKey(String s) { reviewSpaceKey = s; }
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
   void markReviewedPages(PageTree t, String reviewPage, String reviewId, Date date)
   {
      log("markReviewedPages("+t.id+", '"+reviewPage+"', '"+reviewId+"')");
      if (!t.root)
      {
         Page page = pageManager.getPage(t.id);
         if (t.reviewed)
         {
            contentPropertyManager.setStringProperty(page, "pdfreview.lastReviewedOn", ReviewStatus.tagDateFormat.format(date));
            
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

   String indexReview(Page reviewIndexPage, Page reviewPage, User user, int state)
   {
      String reviewId = contentPropertyManager.getStringProperty(reviewIndexPage, "pdfreview.maxId");
      try {
         reviewId = Integer.toString(Integer.parseInt(reviewId) + 1);
      } catch (Exception e) {
         reviewId = "1";
      }
      contentPropertyManager.setStringProperty(reviewIndexPage, "pdfreview.maxId", reviewId);

      contentPropertyManager.setStringProperty(reviewPage, "pdfreview.indexPageId", Long.toString(reviewIndexPage.getId()));

      Date date = new Date();

      ReviewStatus.updateStatus(contentPropertyManager, reviewIndexPage, user, reviewId, state, date, /*create=*/true);

      contentPropertyManager.setStringProperty(reviewIndexPage, "pdfreview."+reviewId+".page", Long.toString(reviewPage.getId()));
      if (reviewPages != null && !reviewPages.isEmpty())
         contentPropertyManager.setTextProperty(reviewIndexPage, "pdfreview."+reviewId+".pages", reviewPages);
      contentPropertyManager.setStringProperty(reviewIndexPage, "pdfreview."+reviewId+".owner", user.getName());
      contentPropertyManager.setStringProperty(reviewIndexPage, "pdfreview."+reviewId+".created", ReviewStatus.tagDateFormat.format(date));

      return reviewId;
   }

   public void setFile(String s) { pdfFile = s; }

   public void setPdfcontent(File f) { pdfContent = f; }

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
         + "<li>"+reviewSpaceKey+"</li>"
         + (pdfContent==null?"<li>pdfContent == null</li>"
         : "<li><![CDATA["+pdfContent.length() + " : " + pdfContent.getAbsolutePath()+"]]></li>")
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
	  try // if a multipart post, try to fetch the uploaded file 
	  {
         File f = FileUploadUtils.getSingleFile();
	
         if (f != null)
            pdfContent = f;
	  }
	  catch (FileUploadException e)
	  {
		  error = e.toString();
		  return "error";
	  }
	  catch (ClassCastException e)
	  {
		  // swallow this; assume its a failure to cast to a multipart message one that isn't
	  }
	   
	  if (reviewPath == null || (pdfFile == null && pdfContent == null))
		 return "input";
	  
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
      Page defaultHome = null;
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

         // set the space theme to the documentation theme
         // and configure the navigation pane for this space
         //
         themeManager.setSpaceTheme(reviewSpaceKey,
               "com.atlassian.confluence.plugins.doctheme:documentation");

         bandanaManager.setValue(
            new ConfluenceBandanaContext(reviewSpaceKey),
            "com.atlassian.confluence.plugins.doctheme",
            new com.atlassian.confluence.plugins.doctheme.Settings(
               "", "", ""
             + "{livesearch:spaceKey="+reviewSpaceKey+"}\n"
             + "h2. Review page tree\n"
             + "{pagetree:root=Home|searchbox=false}\n"
             + "h2. Review index\n"
             + "{review-index:page=Review Index}\n"
             , false));

         List<SpacePermission> perms = new ArrayList<SpacePermission>();

         perms.add(SpacePermission.createAnonymousSpacePermission(SpacePermission.VIEWSPACE_PERMISSION, reviewSpace));
         perms.add(SpacePermission.createGroupSpacePermission(SpacePermission.CREATEEDIT_PAGE_PERMISSION, reviewSpace, "confluence-users"));
         perms.add(SpacePermission.createGroupSpacePermission(SpacePermission.CREATE_ATTACHMENT_PERMISSION, reviewSpace, "confluence-users"));
         perms.add(SpacePermission.createGroupSpacePermission(SpacePermission.REMOVE_ATTACHMENT_PERMISSION, reviewSpace, "confluence-users"));
         perms.add(SpacePermission.createGroupSpacePermission(SpacePermission.COMMENT_PERMISSION, reviewSpace, "confluence-users"));

         reviewSpace.setPermissions(perms);

         // clear the default home page, it will be set
         // to the review index page on creation below.
         // if not updated as part of this review it will
         // be deleted.
         //
         defaultHome = reviewSpace.getHomePage();
         defaultHome.setBodyAsString("");
      }

      if (reviewPath.length() != 0 && reviewPath.charAt(0) == '/')
    	  reviewPath = reviewPath.substring(1);
      
      String[] tree = reviewPath.split("/");

      Page reviewPage = null;

      DefaultSaveContext ctx = new DefaultSaveContext(true, true, false);

      Page reviewIndexPage = pageManager.getPage(reviewSpaceKey, "Review Index");
      if (reviewIndexPage == null)
      {
         reviewIndexPage = new Page();
         reviewIndexPage.setSpace(reviewSpace);
         reviewIndexPage.setTitle("Review Index");
         reviewIndexPage.setContent("{review-index}");
         try
         {
            pageManager.saveContentEntity(reviewIndexPage, ctx);
         }
         catch (Exception ex)
         {
            log("Creation of review index page failed. "+ex.toString());
         }
         reviewSpace.setHomePage(reviewIndexPage);
      }

      for (String s : tree)
      {
         Page p = pageManager.getPage(reviewSpaceKey, s);

         log("Page "+p+" ["+s+"]");

         if (p == defaultHome)
         {
            // dont delete default home as it is touched by this review
            defaultHome = null;
         }

         if (p == null)
         {
            p = new Page();
            p.setSpace(reviewSpace);
            p.setTitle(s);
            p.setBodyAsString("");
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

      if (defaultHome != null)
      {
         // default home was created by space creation but not referenced, delete it
         pageManager.removeContentEntity(defaultHome);
         defaultHome = null;
      }


      ctx.setMinorEdit(false);

      String existingContent = reviewPage.getBodyAsString();

      log("Getting prop from  " + reviewPage.getTitle());

      String indexPageId = contentPropertyManager.getStringProperty(reviewPage, "pdfreview.indexPageId");

      log("indexPageId = " +indexPageId);

      String reviewId = indexReview(reviewIndexPage, reviewPage, user, ReviewStatus.Submitted);

      log("reviewId = "+reviewId);

      Page oldPage = null;
      if (indexPageId != null)
        oldPage = (Page) reviewPage.clone();

      Date date = new Date();
      SimpleDateFormat headingDateFormat = new SimpleDateFormat("d MMMM yyyy (yyyyMMdd-HHmmss)");

      String reviewTag = reviewId + "-" + reviewPage.getTitle().replace(" ","-");

      log("reviewTag = " +reviewTag);
      String reviewHeading = "Review " + reviewId + ": " + headingDateFormat.format(date);

      log("reviewHeading = " +reviewHeading);

      markReviewedPages(reviewPageTree, Long.toString(reviewPage.getId()), reviewId, date);
      
      reviewPage.setBodyAsString(
    	 "<ac:macro ac:name='anchor'><ac:default-parameter>"+reviewId+"</ac:default-parameter></ac:macro>"
       + "<h1>" + reviewHeading + "</h1>"
       + "<ac:macro ac:name='section'><ac:parameter ac:name='border'>true</ac:parameter><ac:rich-text-body>"
       + "<ac:macro ac:name='column'><ac:parameter ac:name='width'>55%</ac:parameter><ac:rich-text-body>"
       + (pdfContent==null
         ?("<h3>This review includes the following pages</h3>"
          +"<ac:macro ac:name='unmigrated-inline-wiki-markup'><ac:plain-text-body><![CDATA["+wikiFormatTree(reviewPageTree, "")+"]]></ac:plain-text-body></ac:macro>"
          )
         :"<h3>Review of non-wiki document.</h3>")
       + "<h3>Review progress</h3>"
       + "<ac:macro ac:name='unmigrated-inline-wiki-markup'><ac:plain-text-body><![CDATA[{review-progress:id="+reviewId+"}]]></ac:plain-text-body></ac:macro>"
       + "</ac:rich-text-body></ac:macro>"
       + "<ac:macro ac:name='column'><ac:parameter ac:name='width'>45%</ac:parameter><ac:rich-text-body>"
       + "<ac:macro ac:name='viewpdf'><ac:parameter ac:name='width'>100%</ac:parameter><ac:default-parameter>"+reviewTag+".pdf</ac:default-parameter></ac:macro>"
       + "</ac:rich-text-body></ac:macro>"
       + "</ac:rich-text-body></ac:macro>"
       + "<hr />"
       + existingContent
       );

      log("content updated");

      File file;
      if (pdfFile != null)
    	  file = new File(pdfFile.replace(bootstrapManager.getWebAppContextPath() + "/download", bootstrapManager.getConfluenceHome()));
      else if (pdfContent != null)
    	  file = pdfContent;
      else
    	  return "error";

      Attachment attachment = new Attachment(reviewTag+".pdf", "application/pdf", file.length(), "Snapshot for review " + reviewId);

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

      redirect = reviewPage.getUrlPath();

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
   }
}

