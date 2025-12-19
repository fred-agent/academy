package fredlab.dev.samples.a2a.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class JsonRpc {

  public record Request(String jsonrpc, Object id, String method, Object params) {}

  public record Response(String jsonrpc, Object id, Object result, Error error) {
    @NonNull
    public static Response ok(Object id, Object result) {
      return new Response("2.0", id, result, null);
    }
    @NonNull
    public static Response err(Object id, int code, String message) {
      return new Response("2.0", id, null, new Error(code, message, null));
    }
  }

  public record Error(int code, String message, Object data) {}
}
