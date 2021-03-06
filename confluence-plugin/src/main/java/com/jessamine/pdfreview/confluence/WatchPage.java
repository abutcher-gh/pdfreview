package com.jessamine.pdfreview.confluence;

import java.io.PrintWriter;
import java.io.StringWriter;

import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.confluence.core.ContentPropertyManager;
import com.atlassian.confluence.mail.notification.NotificationManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.user.User;

public class WatchPage extends ConfluenceActionSupport {
	private static final long serialVersionUID = -7420092131908749335L;
	private PageManager pageManager;
	private NotificationManager notificationManager;
	private ContentPropertyManager contentPropertyManager;

	WatchPage(PageManager pageManager, NotificationManager notificationManager,
			ContentPropertyManager contentPropertyManager) {
		this.pageManager = pageManager;
		this.notificationManager = notificationManager;
		this.contentPropertyManager = contentPropertyManager;
	}

	private String page;

	public String getPage() {
		return page;
	}

	private Page thePage;

	public void setPage(String s) {
		page = s;
		try {
			thePage = pageManager.getPage(Long.parseLong(s));
		} catch (Exception e) {
		}
	}

	private String reviewId;

	public void setId(String s) {
		reviewId = s;
	}

	public String getError() {
		return error;
	}

	private String error;

	public String execute() {
		// FIXME: ugly catch all
		try {

			if (thePage == null)
				return "error";

			User user = AuthenticatedUserThreadLocal.getUser();
			if (user == null) {
				error = "Not authenticated.  Cannot proceed.";
				return "error";
			}

			if (error == null || error.isEmpty()) {
				notificationManager.addContentNotification(user, thePage);

				String indexPageId = contentPropertyManager.getStringProperty(
						thePage, "pdfreview.indexPageId");

				if (indexPageId == null || indexPageId.isEmpty()) {
					error = "<p><strong>Error:</strong> no review index associated.</p>";
					return "error";
				}

				Page indexPage = (Page) pageManager.getPage(
						Long.parseLong(indexPageId)).getLatestVersion();

				if (indexPage == null) {
					error = "<p><strong>Error:</strong> review index page '"
							+ indexPageId + "' invalid.</p>";
					return "error";
				}

				if (!ReviewStatus.updateStatus(contentPropertyManager,
						indexPage, user, reviewId, ReviewStatus.UnderReview))
					return "error";

				return "success";
			} else
				return "error";

		} catch (Exception e) {
			StringWriter stack = new StringWriter();
			e.printStackTrace(new PrintWriter(stack));
			error = e.toString() + "\n" + stack.toString();
			return "error";
		}
	}
}
