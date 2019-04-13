package cn.net.polyglot.verticle.community

import cn.net.polyglot.config.*
import cn.net.polyglot.module.md5
import cn.net.polyglot.verticle.user.UserVerticle
import cn.net.polyglot.verticle.web.ServletVerticle
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.eventbus.sendAwait
import io.vertx.kotlin.core.file.*
import java.io.File.separator

class LoginVerticle : ServletVerticle() {

  override suspend fun doPost(json: JsonObject, session: Session): Response {

    val reqJson = when(json.getString(PATH)){
      "/register" -> {
        val requestJson =
          json.getJsonObject(FORM_ATTRIBUTES)
            .put(SUBTYPE, REGISTER)

        val password = md5(requestJson.getString(PASSWORD))
        val password2 = md5(requestJson.getString(PASSWORD2))

        requestJson.put(PASSWORD, password)
        requestJson.put(PASSWORD2, password2)

        val result = vertx.eventBus().sendAwait<JsonObject>(UserVerticle::class.java.name,requestJson).body()
        if(!result.containsKey(REGISTER) || !result.getBoolean(REGISTER)){
          return Response(ResponseType.TEMPLATE, "register.html")
        }

        JsonObject().put(ID, requestJson.getString(ID))
          .put(PASSWORD, password)
          .put(TYPE, USER)
          .put(SUBTYPE, PROFILE)
      }
      "/update" -> {
        if (session.get(ID) == null) {
          return Response(ResponseType.TEMPLATE, "index.htm")
        }

        val requestJson =
          json.getJsonObject(FORM_ATTRIBUTES)
            .put(TYPE, USER)
            .put(SUBTYPE, UPDATE)
            .put(ID, session.get(ID))
        //update user data
        vertx.eventBus().sendAwait<JsonObject>(UserVerticle::class.java.name,requestJson).body()
        //update profile image
        val uploadFile = json.getJsonObject(UPLOAD_FILES).getString("portrait")
        if(!uploadFile.isNullOrBlank()){

          val jarDir = config.getString(JAR_DIR)
          val dir = config.getString(DIR)

          val file = vertx.fileSystem().propsAwait(jarDir + separator + uploadFile)
          if(file.size()==0L)
            vertx.fileSystem().deleteAwait(jarDir + separator + uploadFile)
          else
            vertx.fileSystem().moveAwait(jarDir + separator + uploadFile,
            dir + separator + session.get(ID) + separator + "portrait", copyOptionsOf().setReplaceExisting(true))
        }

        JsonObject().put(ID, session.get(ID))
          .put(PASSWORD, session.get(PASSWORD))
          .put(TYPE, USER)
          .put(SUBTYPE, PROFILE)
      }
      else -> {//default is login
        val id = json.getJsonObject(FORM_ATTRIBUTES).getString(ID)
        val password = md5(json.getJsonObject(FORM_ATTRIBUTES).getString(PASSWORD))

        JsonObject().put(ID, id)
          .put(PASSWORD, password)
          .put(TYPE, USER)
          .put(SUBTYPE, PROFILE)
      }
    }

    return profile(reqJson, session)
  }

  override suspend fun doGet(json: JsonObject, session: Session): Response {
    if (session.get(ID) == null) {
      return Response(ResponseType.TEMPLATE, "index.htm")
    }

    val id = session.get(ID)
    val password = session.get(PASSWORD)

    return when(json.getString(PATH)){
      "/update" -> {
        profile(JsonObject().put(ID, id)
          .put(PASSWORD, password)
          .put(TYPE, USER)
          .put(SUBTYPE, PROFILE),session,"update.html")
      }
      "/portrait" ->{
        val dir = config.getString(DIR)
        return Response(dir+ separator+session.get(ID)+ separator+"portrait")
      }
      else -> {
        profile(JsonObject().put(ID, id)
          .put(PASSWORD, password)
          .put(TYPE, USER)
          .put(SUBTYPE, PROFILE),session)
      }
    }
  }

  private suspend fun profile(reqJson: JsonObject, session: Session, defaultTemplatePath:String = "index.html"):Response{
    return try{
      val asyncResult = vertx.eventBus().sendAwait<JsonObject>(UserVerticle::class.java.name, reqJson).body()

      if(asyncResult.containsKey(PROFILE) && asyncResult.getBoolean(PROFILE)){

        session.put(ID, reqJson.getString(ID))
        session.put(PASSWORD, reqJson.getString(PASSWORD))
        session.put(NICKNAME, asyncResult.getJsonObject(JSON_BODY).getString(NICKNAME))

        Response(defaultTemplatePath, asyncResult.getJsonObject(JSON_BODY))
      }else{
        Response(ResponseType.TEMPLATE, "index.htm")
      }
    }catch (e:Throwable){
      e.printStackTrace()
      Response(ResponseType.TEMPLATE, "error.htm")
    }
  }
}
