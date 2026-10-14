import { createClient } from "npm:@supabase/supabase-js@2";

export function clients(req: Request) {
  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) throw new Error("Supabase runtime secrets are missing.");

  const authorization = req.headers.get("Authorization") ?? "";
  const userClient = createClient(url, anonKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false },
  });
  const adminClient = createClient(url, serviceKey, { auth: { persistSession: false } });
  return { userClient, adminClient };
}

export async function requireUser(req: Request) {
  const { userClient, adminClient } = clients(req);
  const { data, error } = await userClient.auth.getUser();
  if (error || !data.user) throw new Error("로그인이 필요합니다.");
  return { user: data.user, userClient, adminClient };
}
