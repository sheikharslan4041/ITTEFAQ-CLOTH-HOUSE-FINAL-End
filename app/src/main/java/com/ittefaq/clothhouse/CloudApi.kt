package com.ittefaq.analytics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Supabase REST client; never put a service-role key in an Android application. */
class CloudApi(private val context:Context){
 private val prefs=context.getSharedPreferences("cloud_auth",Context.MODE_PRIVATE)
 private val base=BuildConfig.SUPABASE_URL.trimEnd('/')
 private val key=BuildConfig.SUPABASE_ANON_KEY
 private fun request(path:String,method:String="GET",payload:String?=null,authenticated:Boolean=true):String{
  require(base.startsWith("https://")&&key.isNotBlank()){"Configure Supabase URL and publishable/anon key in local.properties"}
  val conn=URL("$base$path").openConnection() as HttpURLConnection
  conn.requestMethod=method;conn.connectTimeout=15000;conn.readTimeout=20000
  conn.setRequestProperty("apikey",key);conn.setRequestProperty("Content-Type","application/json")
  if(authenticated){val token=prefs.getString("access_token",null)?:error("Sign in first");conn.setRequestProperty("Authorization","Bearer $token")}
  if(method=="POST")conn.setRequestProperty("Prefer","resolution=merge-duplicates,return=minimal")
  if(payload!=null){conn.doOutput=true;conn.outputStream.use{it.write(payload.toByteArray(Charsets.UTF_8))}}
  val code=conn.responseCode;val body=(if(code in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}?:""
  conn.disconnect();if(code !in 200..299)error("Cloud HTTP $code: ${body.take(400)}")
  return body
 }
 fun signIn(email:String,password:String):String{
  val body=request("/auth/v1/token?grant_type=password","POST",JSONObject().put("email",email).put("password",password).toString(),false)
  val obj=JSONObject(body);prefs.edit().putString("access_token",obj.getString("access_token")).apply()
  return "Signed in successfully. Session is saved on this phone."
 }
 private fun validateBusiness(id:String):String{require(Regex("[0-9a-fA-F-]{36}").matches(id)){"Enter a valid business UUID"};return id}
 fun upload(db:Store,businessId:String):String{
  val id=validateBusiness(businessId);val months=db.months();require(months.isNotEmpty()){"No local months to upload"}
  val deps=db.departments();val data=JSONArray()
  for(month in months){val (sales,profit,liability)=db.month(month);val stock=JSONObject();db.stocks(month).forEach{(departmentId,value)->stock.put(deps.firstOrNull{it.first==departmentId}?.second?:return@forEach,value)}
   data.put(JSONObject().put("business_id",id).put("month",month+"-01").put("sales",sales).put("profit",profit).put("liability",liability).put("stock",stock))}
  request("/rest/v1/monthly_records?on_conflict=business_id,month","POST",data.toString());return "Uploaded ${months.size} month(s). Changes from other phones may be overwritten for matching months."
 }
 fun download(db:Store,businessId:String):String{
  val id=validateBusiness(businessId)
  val body=request("/rest/v1/monthly_records?business_id=eq.$id&select=month,sales,profit,liability,stock&order=month.asc")
  val rows=JSONArray(body);var count=0
  for(i in 0 until rows.length()){
   val item=rows.getJSONObject(i);val month=item.getString("month").take(7);val stock=item.getJSONObject("stock")
   val stocks=mutableMapOf<Int,Double>();val names=stock.keys();while(names.hasNext()){
    val name=names.next();var dept=db.departments().firstOrNull{it.second==name};if(dept==null){db.addDepartment(name);dept=db.departments().firstOrNull{it.second==name}}
    if(dept!=null)stocks[dept.first]=stock.getDouble(name)
   }
   db.save(month,item.getDouble("sales"),item.getDouble("profit"),item.getDouble("liability"),stocks);count++
  }
  return "Downloaded $count month(s). Return to dashboard to view records."
 }
}
