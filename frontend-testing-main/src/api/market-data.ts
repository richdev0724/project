import axios from 'axios'

export const getLitPoolTopActivity = async () => {
  return axios.get('/api/market-data/lit-pool/top-activity')
}
