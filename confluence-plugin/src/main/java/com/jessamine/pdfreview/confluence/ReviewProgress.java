package com.jessamine.pdfreview.confluence;

import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import com.atlassian.confluence.core.ContentPropertyManager;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.renderer.PageContext;
import com.atlassian.confluence.setup.BootstrapManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;
import com.atlassian.user.User;
import com.opensymphony.webwork.ServletActionContext;

public class ReviewProgress extends BaseMacro {
	private SettingsManager settingsManager;
	private BootstrapManager bootstrapManager;
	private PageManager pageManager;
	private AttachmentManager attachmentManager;
	private ContentPropertyManager contentPropertyManager;

	public boolean isInline() {
		return false;
	}

	public boolean hasBody() {
		return false;
	}

	public RenderMode getBodyRenderMode() {
		return RenderMode.NO_RENDER;
	}

	ReviewProgress(SettingsManager settingsManager,
			BootstrapManager bootstrapManager, PageManager pageManager,
			AttachmentManager attachmentManager, SpaceManager spaceManager,
			ContentPropertyManager contentPropertyManager) {
		this.settingsManager = settingsManager;
		this.bootstrapManager = bootstrapManager;
		this.pageManager = pageManager;
		this.attachmentManager = attachmentManager;
		this.contentPropertyManager = contentPropertyManager;
	}

	public String execute(@SuppressWarnings("rawtypes") Map params,
			String body, RenderContext renderContext) throws MacroException {
		PageContext pageContext = (PageContext) renderContext;
		Page page = (Page) pageContext.getEntity();

		StringBuilder rc = new StringBuilder();

		String id = (String) params.get("id");

		if (id == null || id.isEmpty())
			return "<p><strong>Error:</strong> no review id provided.</p>";

		String indexPageId = contentPropertyManager.getStringProperty(page,
				"pdfreview.indexPageId");

		if (indexPageId == null || indexPageId.isEmpty())
			return "<p><strong>Error:</strong> no review index associated.</p>";

		Page indexPage = (Page) pageManager
				.getPage(Long.parseLong(indexPageId)).getLatestVersion();

		if (indexPage == null)
			return "<p><strong>Error:</strong> review index page '"
					+ indexPageId + "' invalid.</p>";

		String tag = id + "-" + page.getTitle().replace(" ", "-");

		StringBuilder path = new StringBuilder(page.getTitle());
		for (Page parent = page.getParent(); parent != null; parent = parent
				.getParent())
			path.insert(0, parent.getTitle() + "/");

		String webdavUrl = settingsManager.getGlobalSettings().getBaseUrl()
				+ "/plugins/servlet/confluence/default/Global/"
				+ page.getSpaceKey() + "/" + path.toString();

		webdavUrl = webdavUrl.replace("Global/~", "Personal/~");

		String participate;

		String authcookie = "";
		String token = "";

		String startReviewURL = bootstrapManager.getWebAppContextPath()
				+ "/plugins/pdfreview/start-review.action";

		int status = ReviewStatus.getStatus(contentPropertyManager, indexPage, id);
		String owner = ReviewStatus.getOwner(contentPropertyManager, indexPage, id);
		boolean closed = status == ReviewStatus.Abandoned
				      || status == ReviewStatus.Completed;
		User user = AuthenticatedUserThreadLocal.getUser();
		List<Attachment> attachments = attachmentManager.getAllVersionsOfAttachments(page);
		StringBuilder commentTableBody = new StringBuilder();
		
		for (Attachment a : attachments) {
			String name = a.getFileName();

			if (!name.endsWith(".fdf"))
				continue;
			if (!name.startsWith(tag))
				continue;

			commentTableBody.append("<tr>");
			commentTableBody.append("<td class='confluenceTd'>" + a.getLastModifierName() + "</td>");
			commentTableBody.append("<td class='confluenceTd'>" + a.getLastModificationDate() + "</td>");
			commentTableBody.append("</tr>");
		}
		
		boolean hasComments = commentTableBody.length() > 0;
		
		HttpServletRequest request = ServletActionContext.getRequest();
		if (request != null) {
			for (Cookie c : request.getCookies()) {
				String name = c.getName();
				if (name.equals("JSESSIONID") || name.equals("remember")
						|| name.startsWith("seraph."))
					authcookie += name + "=" + c.getValue() + "; ";
			}
			if (!authcookie.isEmpty())
				authcookie = "&amp;cookie=" + authcookie;
		}

		String actionText = null;

		if (closed) {
			participate = "<p>This review is <strong>closed</strong>.  ";
			if (user != null && user.getName().equals(owner))
				participate += "As the owner you may <a href='"
						+ startReviewURL
						+ "?reviewIndex=" + indexPage.getIdAsString()
						+ "&reviewId=" + id
						+ "&newStatus=" + ReviewStatus.ReOpened
						+ "'><strong>re-open it</strong></a>.";
			else
				participate += "Only the owner, " + owner + ", may re-open it.";
			participate += "</p>";

			actionText = "View final content";
		} else if (user == null)
			participate = "<p><strong>Note:</strong> You need to be logged in to be able to participate in this review.</p>";
		else {
			if (user.getName().equals(owner)) {
				if (hasComments)
					actionText = "Respond to review comments";
				else
					actionText = "Provide initial comments";

				participate = "<p>As the owner of this review you may <a href='"
						+ startReviewURL
						+ "?reviewIndex=" + indexPage.getIdAsString()
						+ "&reviewId=" + id
						+ "&newStatus=" + ReviewStatus.Completed
						+ "'><strong>mark it as complete</strong></a>, "
						+ "or <a href='" + startReviewURL
						+ "?reviewIndex=" + indexPage.getIdAsString()
						+ "&reviewId=" + id
						+ "&newStatus=" + ReviewStatus.Abandoned
						+ "'><strong>abandon it</strong></a>.</p>";
			} else {
				if (status == ReviewStatus.Submitted
						|| (status == ReviewStatus.ReOpened && !hasComments))
					actionText = "Be the first to review this content";
				else
					actionText = "Review latest content";
				participate = "";
			}
		}

		if (actionText != null)
			participate += "<h3 class='review-action-message'><a id='" + tag
					+ "' href=\"pdfreview:"	+ webdavUrl
					+ "?tag=" + tag
					+ "&amp;user=" + user.getName()
					+ "&amp;page=" + page.getId()
					+ authcookie + token
					+ "\">" + actionText + "</a></h3>"
					+ "<p><strong>Note:</strong> If the link above doesn't attempt to "
					+ "start the desktop review tool then you need to install the "
					+ "<tt>pdfreview</tt> URL scheme and scripts from <a href=\""
					+ ClientCheck.clientUrl
					+ "\">here</a>.</p>"
					+ "<p><strong>BUG:</strong> The first time you go to the link "
					+ "(assuming the desktop tool is installed) the link may fail "
					+ "due to authorization.  If this occurs reload this page and "
					+ "try again &mdash; it is as yet unknown as to why this happens "
					+ "for some clients.</p>";

		rc.append("<div class='aui-message info'>");
		rc.append("<p><strong>Status:</strong> "
				+ ReviewStatus.codeToString(ReviewStatus.getStatus(
						contentPropertyManager, indexPage, id)) + "</p>");
		rc.append("</div>");

		if (hasComments) {
			rc.append("<div class='table-wrap'><table class='confluenceTable aui aui-table'>\n");
			rc.append("<thead>\n");
			rc.append("<th class='confluenceTh'>Reviewer</th>");
			rc.append("<th class='confluenceTh'>Date</th>");
			rc.append("</thead>\n");
			rc.append("<tbody>\n");
			rc.append(commentTableBody.toString());
			rc.append("</tbody>\n");
			rc.append("</table></div>\n");
		} else {
			rc.append("<div class='aui-message info'>There are no review comments as yet.</div>");
		}

		rc.append("<div class='aui-message'>");
		rc.append(participate);
		rc.append("</div>");

		return rc.toString();
	}
}
