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
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.user.User;

import com.opensymphony.webwork.ServletActionContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Cookie;


public class ReviewProgress extends BaseMacro
{
   private SettingsManager settingsManager;
   private PageManager pageManager;
   private AttachmentManager attachmentManager;
   private SpaceManager spaceManager;
   private ContentPropertyManager contentPropertyManager;

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

   ReviewProgress(SettingsManager settingsManager, PageManager pageManager, AttachmentManager attachmentManager, SpaceManager spaceManager, ContentPropertyManager contentPropertyManager)
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

      String id = (String) params.get("id");

      if (id == null || id.isEmpty())
         return "<p><strong>Error:</strong> no review id provided.</p>";

      String indexPageId = contentPropertyManager.getStringProperty(page, "pdfreview.indexPageId");

      if (indexPageId == null || indexPageId.isEmpty())
         return "<p><strong>Error:</strong> no review index associated.</p>";

      Page indexPage = (Page) pageManager.getPage(Long.parseLong(indexPageId)).getLatestVersion();

      if (indexPage == null)
         return "<p><strong>Error:</strong> review index page '"+indexPageId+"' invalid.</p>";

      String tag = id + "-" + page.getTitle().replace(" ","-");

      StringBuilder path = new StringBuilder(page.getTitle());
      for (Page parent = page.getParent(); parent != null; parent = parent.getParent())
         path.insert(0, parent.getTitle() + "/");

      String webdavUrl =
           settingsManager.getGlobalSettings().getBaseUrl()
         + "/plugins/servlet/confluence/default/Global/"
         + page.getSpaceKey()
         + "/" + path.toString();

      webdavUrl = webdavUrl.replace("Global/~", "Personal/~");

      String participate;

      String authcookie = "";
      String token = "";

      HttpServletRequest request = ServletActionContext.getRequest();
      if (request != null)
      {
         for (Cookie c : request.getCookies())
         {
            String name = c.getName();
            if (name.equals("JSESSIONID") || name.equals("remember") || name.startsWith("seraph."))
               authcookie += name + "=" + c.getValue() + "; ";
         }
         if (!authcookie.isEmpty())
            authcookie = "&amp;cookie=" + authcookie;

         // token = new com.atlassian.xwork.SimpleXsrfTokenGenerator().generateToken(req);
         // if (token != null && !token.isEmpty())
         //    token = "&amp;token=" + token;
      }

      // get the currently logged in user and display his name
      User user = AuthenticatedUserThreadLocal.getUser();
      if (user == null)
         participate = "<p><strong>Note:</strong> You need to be logged in to be able to participate in this review.</p>";
      else
         participate = "<h3><a id='"+tag+"' href=\"pdfreview:" + webdavUrl
               + "?tag=" + tag + "&amp;user="+user.getName()+ "&amp;page="+page.getId() + authcookie + token + "\"><u>Review latest content</u></a></h3>"
               + "<p><strong>Note:</strong> If the link above doesn't work for you then you need to install the <tt>pdfreview</tt> URL scheme and scripts from <a href=\"" + ClientCheck.clientUrl + "\">here</a>.</p>"
               ;

      rc.append("<p><strong>Status:</strong> "
            + ReviewStatus.codeToString(
               ReviewStatus.getStatus(contentPropertyManager, indexPage, id))
            + "</p>");

      rc.append("<div class='table-wrap'><table class='confluenceTable'>\n");
      rc.append("<thead>\n");
      rc.append("<th class='confluenceTh'>Author</th>");
      rc.append("<th class='confluenceTh'>Date</th>");
      //rc.append("<th class='confluenceTh'>Time spent</th>");
      //rc.append("<th class='confluenceTh'>Comment</th>");
      rc.append("</thead>\n");
      rc.append("<tbody>\n");

      List<Attachment> attachments = attachmentManager.getAttachments(page);

      for (Attachment a : attachments)
      {
         String name = a.getFileName();

         if (!name.endsWith(".fdf"))
            continue;
         if (!name.startsWith(tag))
            continue;

         rc.append("<tr>");
         rc.append("<td class='confluenceTd'>"+a.getLastModifierName()+"</td>");
         rc.append("<td class='confluenceTd'>"+a.getLastModificationDate()+"</td>");
         //rc.append("<td class='confluenceTd'>unspecified</td>");
         //rc.append("<td class='confluenceTd'>"+a.getComment()+"</td>");
         rc.append("</tr>");
      }

      rc.append("</tbody>\n");
      rc.append("</table></div>\n");

      rc.append(participate);

      return rc.toString();
   }
}

