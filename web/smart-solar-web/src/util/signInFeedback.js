// Do not render server detail, correlation IDs or credential-specific explanations.
export function signInFeedback(error) {
  if (error?.status === 429) return {title:'Please wait', message:'Too many sign-in attempts. Wait a moment and try again.'};
  if (error?.unavailable || error?.status >= 500 || !error?.status || error.status < 400)
    return {title:'Connection unavailable', message:"We couldn't reach Smart Solar. Check your connection and try again."};
  return {title:'Sign-in failed', message:'Check your NIC and password and try again.'};
}
