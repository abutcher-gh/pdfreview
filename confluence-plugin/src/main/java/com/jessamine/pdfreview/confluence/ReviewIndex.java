package com.jessamine.pdfreview.confluence;

import java.util.Map;

import com.atlassian.confluence.core.ContentPropertyManager;
import com.atlassian.confluence.pages.AbstractPage;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.renderer.PageContext;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;

public class ReviewIndex extends BaseMacro {
	private SettingsManager settingsManager;
	private PageManager pageManager;
	private ContentPropertyManager contentPropertyManager;

	ReviewIndex(SettingsManager settingsManager, PageManager pageManager,
			AttachmentManager attachmentManager, SpaceManager spaceManager,
			ContentPropertyManager contentPropertyManager) {
		this.settingsManager = settingsManager;
		this.pageManager = pageManager;
		this.contentPropertyManager = contentPropertyManager;
	}

	public boolean isInline() {
		return false;
	}

	public boolean hasBody() {
		return false;
	}

	public RenderMode getBodyRenderMode() {
		return RenderMode.NO_RENDER;
	}

	public static AbstractPage getLatestReviewPage(ContentPropertyManager cpm,
			PageManager pm, Page reviewIndex, String reviewId) {
		try {
			String pageId = cpm.getStringProperty(reviewIndex, "pdfreview."
					+ reviewId + ".page");
			AbstractPage p = pm.getPage(Long.parseLong(pageId));
			if (p == null)
				return p;
			return p.getLatestVersion();
		} catch (Exception e) {
			return null;
		}
	}

	public String execute(@SuppressWarnings("rawtypes") Map params,
			String body, RenderContext renderContext) throws MacroException {
		PageContext pageContext = (PageContext) renderContext;
		Page reviewIndex = (Page) pageContext.getEntity();

		{
			String pageOverride = (String) params.get("page");
			if (pageOverride != null) {
				Page p = pageManager.getPage(reviewIndex.getSpaceKey(),
						pageOverride);
				if (p != null)
					reviewIndex = p;
			}
		}

		StringBuilder rc = new StringBuilder();

		String maxId = contentPropertyManager.getStringProperty(reviewIndex,
				"pdfreview.maxId");

		if (maxId == null || maxId.isEmpty())
			return "<p><strong>Error:</strong> no review index created here.</p>";

		int max = Integer.parseInt(maxId);

		rc.append("<p><ol>");
		for (int i = max; i > 0; --i) {
			String id = Integer.toString(i);

			try {
				AbstractPage p = getLatestReviewPage(contentPropertyManager,
						pageManager, reviewIndex, id);
				if (p == null)
					continue;

				String link = "<a href=\""
						+ settingsManager.getGlobalSettings().getBaseUrl()
						+ p.getUrlPath() + "#"
						+ p.getTitle().replaceAll("[ +]", "") + "-" + id
						+ "\">" + p.getTitle() + "</a>";

				String owner = contentPropertyManager.getStringProperty(
						reviewIndex, "pdfreview." + id + ".owner");
				String created = contentPropertyManager.getStringProperty(
						reviewIndex, "pdfreview." + id + ".created");

				rc.append("<li value='" + id + "'>");
				rc.append("<strong>" + link + "</strong>");
				rc.append("<div style='font-size: 80%'>");
				rc.append("<strong>Created:</strong> " + created + " (" + owner
						+ ")<br/>");
				rc.append("<strong>Changelog:</strong> "
						+ contentPropertyManager.getTextProperty(reviewIndex,
								"pdfreview." + id + ".state") + "<br/>\n");
				rc.append("<strong>Status:</strong> "
						+ ReviewStatus.codeToString(ReviewStatus.getStatus(
								contentPropertyManager, reviewIndex, id))
						+ "<br/>\n");
				rc.append("</div></li>");
			} catch (Exception e) {
				rc.append("<li value='" + id + "'>");
				rc.append("<strong>Error:</strong> " + e.toString());
				rc.append("</li>");
			}
		}
		rc.append("</ol></p>");

		return rc.toString();
	}
}
