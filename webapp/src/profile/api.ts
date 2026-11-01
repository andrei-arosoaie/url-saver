export interface Profile {
  displayName: string;
  pictureUrl: string | null;
  pictureRemoved: boolean;
}
export interface ProfileApi {
  get(signal: AbortSignal): Promise<Profile>;
  removePicture(signal: AbortSignal): Promise<Profile>;
  restorePicture(signal: AbortSignal): Promise<Profile>;
}
async function request(
  method: string,
  path: string,
  signal: AbortSignal,
): Promise<Profile> {
  const response = await globalThis.fetch(path, {
    method,
    signal,
    credentials: "same-origin",
    headers: { Accept: "application/json" },
  });
  if (!response.ok)
    throw new Error(
      method === "DELETE"
        ? "Could not remove your photo. Please try again."
        : method === "PUT"
          ? "Could not restore your photo. Please try again."
          : "Could not load your profile. Please try again.",
    );
  const value = await response.json();
  if (
    typeof value.displayName !== "string" ||
    (value.pictureUrl !== null && typeof value.pictureUrl !== "string") ||
    typeof value.pictureRemoved !== "boolean"
  )
    throw new Error("The server returned an invalid profile.");
  return value;
}
export const profileApi: ProfileApi = {
  get: (signal) => request("GET", "/api/v1/identity/profile", signal),
  removePicture: (signal) =>
    request("DELETE", "/api/v1/identity/profile/picture", signal),
  restorePicture: (signal) =>
    request("PUT", "/api/v1/identity/profile/picture", signal),
};
