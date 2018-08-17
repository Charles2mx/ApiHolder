package com.dhy.apiholder;

import android.support.annotation.NonNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.SingleTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiHolderUtil {
    private final Retrofit retrofit;
    private final Map<Class, Object> apis = new HashMap<>();
    private final boolean isAndroidApi;

    public ApiHolderUtil() {
        this.isAndroidApi = isAndroidApi();
        this.retrofit = getRetrofit(getClient());
    }

    protected boolean isAndroidApi() {
        return true;
    }

    protected OkHttpClient getClient() {
        return new OkHttpClient();
    }

    protected Retrofit getRetrofit(@NonNull OkHttpClient client) {
        return new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .baseUrl("http://www.demo.com/")
                .client(client)
                .build();
    }

    public final <HOLDER> HOLDER createHolderApi(@NonNull Class<HOLDER> apiHolder) {
        Class[] partApis = apiHolder.getInterfaces();
        for (Class api : partApis) {
            updateApi(api);
        }
        return createHolderApi(apis, apiHolder);
    }

    /**
     * update api with @{@link BaseUrl}
     */
    public final <API> void updateApi(@NonNull Class<API> api) {
        updateApi(api, getBaseUrl(api));
    }

    public final <API> void updateApi(@NonNull Class<API> apiClass, @NonNull String baseUrl) {
        Retrofit retrofit = this.retrofit.newBuilder().baseUrl(baseUrl).build();
        apis.put(apiClass, retrofit.create(apiClass));
    }

    /**
     * get url from with @{@link BaseUrl}
     */
    @NonNull
    public static <API> String getBaseUrl(@NonNull Class<API> api) {
        if (api.isAnnotationPresent(BaseUrl.class)) {
            BaseUrl baseUrl = api.getAnnotation(BaseUrl.class);
            return baseUrl.value();
        } else {
            throw new IllegalArgumentException(String.format("%s: MUST ANNOTATE WITH '%s'", api.getName(), BaseUrl.class.getName()));
        }
    }

    /**
     * hold all api in one
     */
    private <HOLDER> HOLDER createHolderApi(@NonNull final Map<Class, Object> apis, @NonNull Class<HOLDER> apiHolder) {
        return (HOLDER) Proxy.newProxyInstance(apiHolder.getClassLoader(), new Class[]{apiHolder}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                Object api = apis.get(method.getDeclaringClass());
                return invokeApi(api, method, args, isAndroidApi);
            }
        });
    }

    protected Object invokeApi(@NonNull Object api, @NonNull Method method, Object[] args, boolean isAndroidApi) throws Throwable {
        Object result = method.invoke(api, args);
        return isAndroidApi ? setAndroidSchedulers(result) : result;
    }

    protected Object setAndroidSchedulers(@NonNull Object result) {
        if (result instanceof Single) {
            return ((Single) result).compose(new SingleTransformer() {
                @Override
                public SingleSource apply(Single upstream) {
                    return upstream.subscribeOn(Schedulers.io())
                            .unsubscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread());
                }
            });
        } else if (result instanceof Observable) {
            return ((Observable) result).compose(new ObservableTransformer() {
                @Override
                public ObservableSource apply(Observable upstream) {
                    return upstream.subscribeOn(Schedulers.io())
                            .unsubscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread());
                }
            });
        }
        return result;
    }
}
