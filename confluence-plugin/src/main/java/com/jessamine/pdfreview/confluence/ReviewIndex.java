package com.jessamine.pdfreview.confluence;

import java.util.Map;
import java.util.List;

import com.atlassian.spring.container.ContainerManager;

import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.confluence.core.ContentPropertyManager;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.renderer.PageContext;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.AbstractPage;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.user.User;

import com.opensymphony.webwork.ServletActionContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Cookie;


public class ReviewIndex extends BaseMacro
{
   private SettingsManager settingsManager;
   private PageManager pageManager;
   private AttachmentManager attachmentManager;
   private SpaceManager spaceManager;
   private ContentPropertyManager contentPropertyManager;

   ReviewIndex(SettingsManager settingsManager, PageManager pageManager, AttachmentManager attachmentManager, SpaceManager spaceManager, ContentPropertyManager contentPropertyManager)
   {
      this.settingsManager = settingsManager;
      this.pageManager = pageManager;
      this.attachmentManager = attachmentManager;
      this.spaceManager = spaceManager;
      this.contentPropertyManager = contentPropertyManager;
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

   public String execute(Map params, String body, RenderContext renderContext)
      throws MacroException
   {
      PageContext pageContext = (PageContext) renderContext;
      Page page = (Page) pageContext.getEntity();

      {
         String pageOverride = (String) params.get("page");
         if (pageOverride != null)
         {
            Page p = pageManager.getPage(page.getSpaceKey(), pageOverride);
            if (p != null)
               page = p;
         }
      }

      StringBuilder rc = new StringBuilder();

      String maxId = contentPropertyManager.getStringProperty(page, "pdfreview.maxId");

      if (maxId == null || maxId.isEmpty())
         return "<p><strong>Error:</strong> no review index created here.</p>";

      int max = Integer.parseInt(maxId);
      
      rc.append("<p><ol>");
      for (int i = max; i > 0; --i)
      {
         String id = Integer.toString(i);

         rc.append("<li value='"+id+"'>");

         try {
            String head;
            String pageId = contentPropertyManager.getStringProperty(page, "pdfreview."+id+".page");
            AbstractPage p = pageManager.getPage(Long.parseLong(pageId));
            if (p != null)
            {
               p = p.getLatestVersion();
               head = "<a href=\""+p.getUrlPath()+"#"+p.getTitle().replace(" ","")+"-"+id+"\">"+p.getTitle()+"</a>";
            }
            else
            {
               head = pageId;
            }

            String owner = contentPropertyManager.getStringProperty(page, "pdfreview."+id+".owner");
            String created = contentPropertyManager.getStringProperty(page, "pdfreview."+id+".created");

            rc.append("<strong>"+head+"</strong>");
            rc.append("<div style='font-size: 80%'>");
            rc.append("<strong>Created:</strong> "+created+" (" + owner + ")<br/>");
            rc.append("<strong>Changelog:</strong> " + contentPropertyManager.getTextProperty(page, "pdfreview."+id+".state") + "<br/>\n");
            rc.append("<strong>Status:</strong> "
                  + ReviewStatus.codeToString(
                     ReviewStatus.getStatus(contentPropertyManager, page, id))
                  + "<br/>\n");
         }
         catch (Exception e)
         {
            rc.append("<strong>Error:</strong> "+e.toString() + "<br/>\n");
         }
         rc.append("</div></li>");
      }
      rc.append("</ol></p>");

      // rc.append("<div class='table-wrap'><table class='confluenceTable'>\n");
      // rc.append("<thead>\n");
      // rc.append("<th class='confluenceTh'>Author</th>");
      // rc.append("<th class='confluenceTh'>Date</th>");
      // //rc.append("<th class='confluenceTh'>Time spent</th>");
      // //rc.append("<th class='confluenceTh'>Comment</th>");
      // rc.append("</thead>\n");
      // rc.append("<tbody>\n");

      // List<Attachment> attachments = attachmentManager.getAttachments(page);

      // for (Attachment a : attachments)
      // {
      //    String name = a.getFileName();

      //    if (!name.endsWith(".fdf"))
      //       continue;
      //    if (!name.startsWith(tag))
      //       continue;

      //    rc.append("<tr>");
      //    rc.append("<td class='confluenceTd'>"+a.getLastModifierName()+"</td>");
      //    rc.append("<td class='confluenceTd'>"+a.getLastModificationDate()+"</td>");
      //    //rc.append("<td class='confluenceTd'>unspecified</td>");
      //    //rc.append("<td class='confluenceTd'>"+a.getComment()+"</td>");
      //    rc.append("</tr>");
      // }

      // rc.append("</tbody>\n");
      // rc.append("</table></div>\n");

      return rc.toString();
   }
}

