import { Platform } from 'react-native'
import { requireOptionalNativeModule } from 'expo-modules-core'

export const Native = Platform.OS === 'android' ? requireOptionalNativeModule('LocationKeepAlive') : null
