package com.jessamine.pdfreview.confluence;

import com.atlassian.confluence.core.ConfluenceActionSupport;

public class ClientCheck extends ConfluenceActionSupport {
	private static final long serialVersionUID = -6840477097021818330L;
	public static final String clientUrl = "https://github.com/abutcher-gh/pdfreview/releases/latest/download/pdf-review-setup.exe";
	public static final double requiredClientVersion = 0.8;

	ClientCheck() {
	}

	private double clientVersion;

	public void setClientVersion(double v) {
		clientVersion = v;
	}

	public double getClientVersion() {
		return clientVersion;
	}

	public String getClientUrl() {
		return clientUrl;
	}

	public double getRequiredClientVersion() {
		return requiredClientVersion;
	}

	public String execute() {
		if (clientVersion >= requiredClientVersion)
			return "success";
		return "error";
	}
}
