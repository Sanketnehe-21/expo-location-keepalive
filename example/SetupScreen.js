// Minimal setup gate: block "start tracking" until the phone can't kill it.
import { useCallback, useEffect, useState } from 'react'
import { View, Text, Button, AppState } from 'react-native'
import AsyncStorage from '@react-native-async-storage/async-storage'
import {
  isBatteryUnrestricted, requestBatteryUnrestricted,
  openAutoStartSettings, brandStep,
} from 'expo-location-keepalive'

const AUTOSTART_KEY = 'keepalive_autostart_confirmed'

export async function isSetupDone() {
  if (!isBatteryUnrestricted()) return false
  if (brandStep() && (await AsyncStorage.getItem(AUTOSTART_KEY)) !== '1') return false
  return true
}

export default function SetupScreen({ appName = 'This app', onDone }) {
  const step = brandStep(appName)
  const [battery, setBattery] = useState(false)
  const [autostart, setAutostart] = useState(!step)

  const refresh = useCallback(async () => {
    setBattery(isBatteryUnrestricted())
    setAutostart(!step || (await AsyncStorage.getItem(AUTOSTART_KEY)) === '1')
  }, [step])

  // Re-check when the user returns from Settings
  useEffect(() => {
    refresh()
    const sub = AppState.addEventListener('change', s => s === 'active' && refresh())
    return () => sub.remove()
  }, [refresh])

  return (
    <View style={{ padding: 24, gap: 16 }}>
      <Text>{battery ? '✅' : '⬜️'} Battery: set {appName} to "Unrestricted"</Text>
      {!battery && <Button title="Allow" onPress={requestBatteryUnrestricted} />}

      {step && (
        <>
          <Text>{autostart ? '✅' : '⬜️'} {step.text}</Text>
          {!autostart && (
            <>
              <Button title="Open settings" onPress={openAutoStartSettings} />
              <Button title="I did it" onPress={async () => { await AsyncStorage.setItem(AUTOSTART_KEY, '1'); setAutostart(true) }} />
            </>
          )}
        </>
      )}

      <Button title="Continue" disabled={!(battery && autostart)} onPress={onDone} />
    </View>
  )
}
