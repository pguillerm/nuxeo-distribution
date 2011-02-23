<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@page import="org.nuxeo.wizard.context.Context"%>
<%@page import="org.nuxeo.wizard.context.ParamCollector"%>
<%@page import="org.nuxeo.wizard.nav.SimpleNavigationHandler"%>
<%@page import="org.nuxeo.wizard.nav.Page"%>
<%@page import="java.util.ResourceBundle"%>
<%@page import="java.util.Map"%>
<%@page import="java.util.HashMap"%>
<fmt:setBundle basename="messages" />
<%
// Global rendering context init
String contextPath = request.getContextPath();
Context ctx = Context.instance(request);
Page currentPage = (Page) request.getAttribute("currentPage");
ParamCollector collector = ctx.getCollector();
SimpleNavigationHandler nav = SimpleNavigationHandler.instance();
%>

<html>

<head>
<title><fmt:message key="label.nuxeo.wizard" /></title>
<link rel="stylesheet" href="<%=contextPath%>/css/nuxeo.css" type="text/css" media="screen" charset="utf-8" />
<script src="<%=contextPath%>/scripts/jquery-1.4.3.min.js"></script>
<script src="<%=contextPath%>/scripts/jquery.simplerss-0.1.0.js"></script>
<script>
function navigateTo(page) {
  window.location.href='<%=contextPath%>/' + page;
}
function showError(id) {
  alert(id);
}
</script>
</head>

<body>

<table width="100%" cellpadding="0" cellspacing="0">
  <tr valign="middle">
    <td class="header">
        <div class="logo">
          <a href="<%=contextPath%>"><img src="<%=contextPath%>/images/logo_connect_white.png" height="28px" border="0"/></a>
        </div>
        <div style="clear:both;"></div>
    </td>
  </tr>
  <tr valign="top" align="left">
    <td class="mainBlock">


<table width="100%">
<tr>
<td width="100px">

<br/><br/><br/><br/><br/>


<%for (Page item : nav.getPages()) {%>

<div
  class="navItem <%=currentPage.getAction().equals(item.getAction()) ? "navItemSelected" : "" %>"
><fmt:message key="<%=item.getLabelKey()%>"/></div>

<%} %>

</td>
<td class="mainClell">

<% if (currentPage.getProgress()>=0) { %>
<center>
<table width="60%">
<tr>
<td colspan="2" style="font-style:italic;font-color:#555555;text-align:center"><fmt:message key="label.nuxeo.wizard.progress" /></td>
</tr>
<tr style="border-style:solid;border-width:1px;border-color:#CCCCCC">
<td width="<%=currentPage.getProgress()%>%" style="background-color:#2888f8;padding:0px;margin:0px">&nbsp;</td>
<td width="<%=100-currentPage.getProgress()%>%" style="background-color:#DDDDDD;padding:0px;margin:0px"></td>
</tr>
</table>
</center>
<%}%>
