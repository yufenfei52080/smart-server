package smart.old.action.alarm;

import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import net.cellcloud.common.Logger;
import net.cellcloud.core.Cellet;
import net.cellcloud.talk.dialect.ActionDialect;
import net.cellcloud.util.ObjectProperty;
import net.cellcloud.util.Properties;

import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.DeferredContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.json.JSONException;
import org.json.JSONObject;

import smart.old.action.AbstractListener;
import smart.old.api.API;
import smart.old.api.RequestContentCapsule;
import smart.old.api.host.HostConfig;
import smart.old.api.host.HostConfigContext;
import smart.old.api.host.MonitorSystemHostConfig;
import smart.old.mast.action.Action;

/**
 * 添加处理意见监听
 * 
 * @author yanbo
 */
public final class AddAlarmOpInfoListener extends AbstractListener {

	public AddAlarmOpInfoListener(Cellet cellet) {
		super(cellet);
	}

	@Override
	public void onAction(ActionDialect action) {

		// 使用同步方式进行请求
		// 因为onAction方法是由cell cloud 的action dialect 进行回调的，
		// 该方法独享一个线程，因此可以在此线程里进行阻塞式的调用。
		// 设置请求HTTP API方式

		// 获取请求参数
		JSONObject json;
		String token = null;
		long almId = 0;
		String moType = null;
		String almCause = null;
		long moId = 0;
		String dealInfo = null;
//		long dealUserId = 0;

		try {
			json = new JSONObject(action.getParamAsString("data"));
			token = json.getString("token");
			almId = json.getLong("almId");
			moType = json.getString("moType");
			almCause = json.getString("almCause");
			moId = json.getLong("moId");
			dealInfo = json.getString("dealInfo");
//			dealUserId = json.getLong("dealUserId");
			
		} catch (JSONException e1) {
			e1.printStackTrace();
		}

		Properties params = new Properties();
		if (token != null && !"".equals(token)) {

			// URL
			HostConfig config = new MonitorSystemHostConfig();
			HostConfigContext context = new HostConfigContext(config);
			StringBuilder url = new StringBuilder(context.getAPIHost())
					.append("/").append(API.ALARMOPINFO).append("/").append(almId).append(",")
					.append(moType).append(",").append(almCause).append(",")
					.append(String.valueOf(moId)).append(",").append(dealInfo)
					.append("?DMSN=998&userID=9980000000000000").append("&op=save");
			System.out.println("请求的URL: "+url.toString());
			
			// 创建请求
			Request request = this.getHttpClient().newRequest(url.toString());
			request.method(HttpMethod.GET);
			url = null;

			// 填写数据内容
			DeferredContentProvider dcp = new DeferredContentProvider();
			RequestContentCapsule capsule = new RequestContentCapsule();
			capsule.append("almId", almId);
			capsule.append("almCause", almCause);
			capsule.append("moId", moId);
			capsule.append("moType", moType);
			capsule.append("dealInfo", dealInfo);
			capsule.append("token", token);
			dcp.offer(capsule.toBuffer());
			dcp.close();
//			request.content(dcp);

			// 发送请求
			ContentResponse response = null;
			try {
				response = request.send();
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			} catch (TimeoutException e1) {
				e1.printStackTrace();
			} catch (ExecutionException e1) {
				e1.printStackTrace();
			}

			JSONObject jo = null;
			switch (response.getStatus()) {
			case HttpStatus.OK_200:
				byte[] bytes = response.getContent();
				if (null != bytes) {

					// 获取从Web服务器上返回的数据
					String content = new String(bytes, Charset.forName("UTF-8"));
					try {
						jo = new JSONObject(content);
						System.out.println("返回："+jo);
						if ("成功".equals(jo.getString("result"))) {
							jo.remove("result");
							jo.put("status", 300);
							jo.put("errorInfo", "");
						} else {
							jo.remove("result");
							jo.put("status", 376);
							jo.put("errorInfo", jo.get("msg"));
						}
						// 设置参数
						params.addProperty(new ObjectProperty("data", jo));

						// 响应动作，即向客户端发送ActionDialect
						// 参数tracker 是一次动作的追踪标识，这里可以使用访问标识token
						this.response(token, Action.ADDALARMOPINFO, params);

					} catch (JSONException e) {
						e.printStackTrace();
					}
				} else {
					this.reportHTTPError(token, Action.ADDALARMOPINFO);
				}
				break;
			default:
				Logger.w(AddAlarmOpInfoListener.class,
						"返回响应码：" + response.getStatus());
				jo = new JSONObject();
				try {
					jo.put("status", 900);
				} catch (JSONException e) {
					e.printStackTrace();
				}

				// 设置参数
				params.addProperty(new ObjectProperty("data", jo));

				// 响应动作，即向客户端发送 ActionDialect
				// 参数 tracker 是一次动作的追踪标识，这里可以使用处理类型。
				this.response(token, Action.ADDALARMOPINFO, params);
				break;
			}
		} else {
			JSONObject jo = new JSONObject();
			try {
				jo.put("status", 800);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			params.addProperty(new ObjectProperty("data", jo));
			this.response(Action.ADDALARMOPINFO, params);
		}
	}
}
