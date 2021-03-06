package smart.old.action.resource;

import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import net.cellcloud.common.Logger;
import net.cellcloud.core.Cellet;
import net.cellcloud.talk.dialect.ActionDialect;
import net.cellcloud.util.ObjectProperty;
import net.cellcloud.util.Properties;

import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import smart.old.action.AbstractListener;
import smart.old.api.API;
import smart.old.api.host.HostConfig;
import smart.old.api.host.HostConfigContext;
import smart.old.api.host.MonitorSystemHostConfig;
import smart.old.mast.action.Action;

/**
 * 网络接口kpi监听器
 * 
 * @author Lianghai Li
 */
public final class InterfaceKpiListener extends AbstractListener {

	public InterfaceKpiListener(Cellet cellet) {
		super(cellet);
	}

	@Override
	public void onAction(ActionDialect action) {

		// 使用同步的方式进行请求
		// 注意：因为onAction方法是由Cell Cloud的action dialect进行回调的
		// 该方法独享一个线程，因此可以在此线程里进行阻塞式的调用
		// 因此，这里可以用同步的方式请求HTTP API

		// 获取参数
		JSONObject json = null;
		long moId = 0;
		int rangeInHour = 0;
		int currentIndex = 0;
		int pageSize = 2;
		try {
			json = new JSONObject(action.getParamAsString("data"));
			moId = json.getLong("moId");
			rangeInHour = json.getInt("rangeInHour");
			currentIndex = json.getInt("currentIndex");
			// pageSize = json.getInt("pageSize");
		} catch (JSONException e1) {
			e1.printStackTrace();
		}

		// URL
		HostConfig config = new MonitorSystemHostConfig();
		HostConfigContext context = new HostConfigContext(config);
		StringBuilder url = new StringBuilder(context.getAPIHost())
				.append("/")
				.append(API.INTERFACEKPI)
				.append("/")
				.append(moId)
				.append("/fInBwUsage,fOutBwUsage,fInRate,fOutRate?rangeInHour=")
				.append(rangeInHour);

		// 创建请求
		Request request = this.getHttpClient().newRequest(url.toString());
		request.method(HttpMethod.GET);

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

		Properties params = new Properties();
		JSONObject data = null;
		switch (response.getStatus()) {
		case HttpStatus.OK_200:
			byte[] bytes = response.getContent();
			if (null != bytes) {
				// 获取从Web服务器上返回的数据
				String content = new String(bytes, Charset.forName("UTF-8"));
				try {
					data = new JSONObject(content);
					System.out.println("ifKpi 源数据：      " + data);
					if ("success".equals(data.get("status"))
							&& (!"".equals(data.get("dataList"))
									&& data.get("dataList") != null
									&& !"null".equals(data.get("dataList")) && !data
									.get("dataList").equals(null))) {
						JSONArray ja = data.getJSONArray("dataList");
						DateFormat df = new SimpleDateFormat(
								"yyyy-MM-dd HH:mm:ss");

						List<Long> mosnList = new ArrayList<Long>();
						for (int i = 0; i < ja.length(); i++) {
							long mosnAll = ja.getJSONObject(i).getLong("mosn");
							if (!mosnList.contains(mosnAll)) {
								mosnList.add(mosnAll);
							}
						}
						// System.out.println("mosnALL   "+mosnList);

						List<Long> subml = new ArrayList<Long>();
						for (int i = 0; i < mosnList.size(); i++) {
							if ((i >= (currentIndex - 1) * pageSize)
									&& (i < (currentIndex * pageSize))) {
								subml.add(mosnList.get(i));
							}
						}

						// System.out.println("mosnSUB   "+subml);
						// if (mosnList.size() > 2) {
						// subml = mosnList.subList(0, 1);
						// } else {
						// subml = mosnList;
						// }

						// System.out.println("subml-size   " + subml.size());
						JSONArray jaSub = new JSONArray();
						for (int i = 0; i < subml.size(); i++) {
							long mosnSub = subml.get(i);

							for (int j = 0; j < ja.length(); j++) {
								JSONObject joAll = ja.getJSONObject(j);
								if (mosnSub == joAll.getLong("mosn")) {
									JSONArray jsData = joAll
											.getJSONArray("data");
									JSONArray jsDa = new JSONArray();

									int l = 0;
									if (jsData.length() > 30) {
										l = 30;
									} else {
										l = jsData.length();
									}
									for (int k = 0; k < l; k++) {
										JSONObject joKpi = new JSONObject();

										if (null == jsData.get(0)
												|| "".equals(jsData.get(0))
												|| "0".equals(jsData.get(0))
												|| "0.0".equals(jsData.get(0))
												|| "null".equals(jsData.get(0))
												|| (jsData.get(0)).equals(null)) {
											joKpi.put("value", 0);
										} else {
											joKpi.put("value", Float
													.valueOf((String) jsData
															.getJSONArray(k)
															.get(0)));
										}

										joKpi.put(
												"collectTime",
												df.parse(
														(String) jsData
																.getJSONArray(k)
																.get(1))
														.getTime());
										jsDa.put(joKpi);
									}

									joAll.remove("data");
									joAll.put("data", jsDa);
									// String mosn1 = joAll.getString("mosn");
									// long mosn = Long.parseLong(mosn1);
									// joAll.put("mosn", mosn);
									joAll.put("mosn", Long.parseLong(joAll
											.getString("mosn")));
									joAll.put("kpi", Long.parseLong(joAll
											.getString("kpi")));
									jaSub.put(joAll);
								}

							}

						}

						data.remove("dataList");
						data.put("dataList", jaSub);
						data.put("moId", moId);
						data.put("status", 300);
						data.put("errorInfo", "");
					} else {
						data.remove("dataList");
						data.put("status", 614);
						data.put("data", "");
						data.put("errorInfo", "未获取到接口kpi数据");
					}
					System.out.println("ifKpiData: " + data);

					// 设置参数
					params.addProperty(new ObjectProperty("data", data));
				} catch (JSONException e) {
					e.printStackTrace();
				} catch (ParseException e) {
					e.printStackTrace();
				}

				// 响应动作，即想客户端发送ActionDialect
				// 参数tracker 是一次动作的追踪表示。
				this.response(Action.INTERFACEKPI, params);
			} else {
				this.reportHTTPError(Action.INTERFACEKPI);
			}
			break;
		default:
			Logger.w(EquipmentBasicListener.class,
					"返回响应码" + response.getStatus());
			try {
				data = new JSONObject();
				data.put("status", 900);
			} catch (JSONException e) {
				e.printStackTrace();
			}

			// 设置参数
			params.addProperty(new ObjectProperty("data", data));

			// 响应动作，即向客户端发送 ActionDialect
			this.response(Action.INTERFACEKPI, params);
			break;
		}

	}

}
